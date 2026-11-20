package com.ticketflow.bookingservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketflow.bookingservice.repository.BookingRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Real Feign clients + Spring Cloud LoadBalancer + Resilience4j, against a local
 * stub of event-service and customer-service (JDK HttpServer). Thresholds come
 * from the test application.yaml (copy of the config repo). Only differences:
 * a short wait in OPEN state (so the HALF_OPEN test does not wait 10 s) and a
 * short read timeout (so the timeout case is fast).
 */
@SpringBootTest(properties = {
        "resilience4j.circuitbreaker.configs.remote-service.wait-duration-in-open-state=1500ms",
        "spring.cloud.openfeign.client.config.event-service.read-timeout=300",
})
@AutoConfigureMockMvc
class FeignCircuitBreakerTest {

    enum Mode { OK, NOT_FOUND, DRAFT, CONFLICT, SERVER_ERROR, SLOW }

    private static final String BOOKING = """
            {"customerId":7,"eventId":42,"quantity":2}
            """;
    private static final String PUBLISHED_EVENT = """
            {"id":42,"status":"PUBLISHED","availableSeats":100,"price":150.00}
            """;

    static final AtomicReference<Mode> customerMode = new AtomicReference<>(Mode.OK);
    static final AtomicReference<Mode> eventMode = new AtomicReference<>(Mode.OK);
    static final AtomicReference<Mode> reserveMode = new AtomicReference<>(Mode.OK);
    static final AtomicInteger eventRequests = new AtomicInteger();
    static final AtomicInteger customerRequests = new AtomicInteger();
    static final AtomicInteger reserveRequests = new AtomicInteger();

    static final HttpServer stub = startStub();

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + stub.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.event-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.customer-service[0].uri", () -> uri);
    }

    @AfterAll
    static void stopStub() {
        stub.stop(0);
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CircuitBreakerRegistry circuitBreakers;
    @Autowired
    TimeLimiterRegistry timeLimiters;
    @Autowired
    BookingRepository repository;

    CircuitBreaker eventBreaker;
    CircuitBreaker customerBreaker;

    @BeforeEach
    void reset() {
        customerMode.set(Mode.OK);
        eventMode.set(Mode.OK);
        reserveMode.set(Mode.OK);
        eventRequests.set(0);
        customerRequests.set(0);
        reserveRequests.set(0);
        eventBreaker = circuitBreakers.circuitBreaker("event-service");
        customerBreaker = circuitBreakers.circuitBreaker("customer-service");
        eventBreaker.reset();
        customerBreaker.reset();
        repository.deleteAll();
    }

    private ResultActions book() throws Exception {
        return mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING));
    }

    @Test
    void oneBreakerPerFeignClientWithTheConfiguredThresholds() {
        // Exactly the two client-level breakers: no per-method ones.
        assertThat(circuitBreakers.getAllCircuitBreakers())
                .extracting(CircuitBreaker::getName)
                .containsExactlyInAnyOrder("event-service", "customer-service");
        var config = eventBreaker.getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(timeLimiters.timeLimiter("event-service").getTimeLimiterConfig().getTimeoutDuration())
                .isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void businessAnswers404And409DoNotCountAsFailures() throws Exception {
        eventMode.set(Mode.NOT_FOUND);
        for (int i = 0; i < 6; i++) {
            book().andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("event not found: 42"));
        }
        eventMode.set(Mode.DRAFT);
        for (int i = 0; i < 3; i++) {
            book().andExpect(status().isConflict());
        }
        eventMode.set(Mode.OK);
        reserveMode.set(Mode.CONFLICT);
        for (int i = 0; i < 6; i++) {
            book().andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("not enough seats for event 42: requested 2"));
        }

        assertThat(eventBreaker.getState()).isEqualTo(State.CLOSED);
        assertThat(eventBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        // 404/409 are ignored (not even buffered); only the successful DRAFT and
        // PUBLISHED GETs are: failure rate 0 %.
        assertThat(eventBreaker.getMetrics().getFailureRate()).isZero();

        // A valid booking still goes through.
        reserveMode.set(Mode.OK);
        book().andExpect(status().isCreated());
    }

    @Test
    void fiveTechnicalFailuresOpenTheCircuitAndTheNextCallIsRejectedWithoutANetworkCall() throws Exception {
        // 5xx and read timeouts both count.
        Mode[] failures = {Mode.SERVER_ERROR, Mode.SLOW, Mode.SERVER_ERROR, Mode.SLOW, Mode.SERVER_ERROR};
        for (int i = 0; i < failures.length; i++) {
            eventMode.set(failures[i]);
            book().andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("event-service is unavailable, try again later"));
            State expected = i < 4 ? State.CLOSED : State.OPEN;
            assertThat(eventBreaker.getState()).as("after failure %d", i + 1).isEqualTo(expected);
        }
        assertThat(eventRequests).hasValue(5);

        // OPEN: rejected before any request is sent, even though event-service is back.
        eventMode.set(Mode.OK);
        book().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("event-service is unavailable (circuit breaker open), try again later"));
        assertThat(eventRequests).hasValue(5);
        assertThat(eventBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);

        // Nothing reserved, nothing saved; the customer breaker is not affected.
        assertThat(reserveRequests).hasValue(0);
        assertThat(repository.count()).isZero();
        assertThat(customerBreaker.getState()).isEqualTo(State.CLOSED);
        assertThat(customerBreaker.getMetrics().getNumberOfFailedCalls()).isZero();

        // The health endpoint shows it.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.circuitBreakers.details.event-service.state").value("OPEN"))
                .andExpect(jsonPath("$.components.circuitBreakers.details.customer-service.state").value("CLOSED"));
    }

    @Test
    void customerOutageOpensOnlyTheCustomerBreaker() throws Exception {
        customerMode.set(Mode.SERVER_ERROR);
        for (int i = 0; i < 5; i++) {
            book().andExpect(status().isServiceUnavailable());
        }
        assertThat(customerBreaker.getState()).isEqualTo(State.OPEN);
        book().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("customer-service is unavailable (circuit breaker open), try again later"));
        assertThat(customerRequests).hasValue(5);

        assertThat(eventBreaker.getState()).isEqualTo(State.CLOSED);
        assertThat(eventRequests).hasValue(0);
    }

    @Test
    void halfOpenThenClosedAfterSuccessfulCalls() throws Exception {
        eventMode.set(Mode.SERVER_ERROR);
        for (int i = 0; i < 5; i++) {
            book().andExpect(status().isServiceUnavailable());
        }
        assertThat(eventBreaker.getState()).isEqualTo(State.OPEN);

        eventMode.set(Mode.OK);
        Thread.sleep(1700); // > wait-duration-in-open-state (1500 ms in this test)

        // One booking = 2 calls to event-service (GET + reservation).
        book().andExpect(status().isCreated());
        assertThat(eventBreaker.getState()).isEqualTo(State.HALF_OPEN);
        // 3rd permitted call succeeds -> failure rate 0 % < 50 % -> CLOSED.
        book().andExpect(status().isCreated());
        assertThat(eventBreaker.getState()).isEqualTo(State.CLOSED);
        assertThat(repository.count()).isEqualTo(2);
    }

    // ---------- stub of event-service + customer-service ----------

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/customers/", exchange -> {
                customerRequests.incrementAndGet();
                switch (customerMode.get()) {
                    case SERVER_ERROR -> reply(exchange, 500, "{}");
                    default -> reply(exchange, 200, "{\"id\":7}");
                }
            });
            server.createContext("/internal/events/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                if (exchange.getRequestURI().getPath().endsWith("/reservations")) {
                    reserveRequests.incrementAndGet();
                    reply(exchange, reserveMode.get() == Mode.CONFLICT ? 409 : 200, PUBLISHED_EVENT);
                    return;
                }
                eventRequests.incrementAndGet();
                switch (eventMode.get()) {
                    case NOT_FOUND -> reply(exchange, 404, "{}");
                    case DRAFT -> reply(exchange, 200, PUBLISHED_EVENT.replace("PUBLISHED", "DRAFT"));
                    case SERVER_ERROR -> reply(exchange, 500, "{}");
                    case SLOW -> {
                        sleep(1000); // > read-timeout (300 ms in this test)
                        reply(exchange, 200, PUBLISHED_EVENT);
                    }
                    default -> reply(exchange, 200, PUBLISHED_EVENT);
                }
            });
            // Concurrent handlers: a SLOW reply must not block the next request.
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException clientGone) {
            // The client already timed out (SLOW): nothing to do.
        } finally {
            exchange.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
