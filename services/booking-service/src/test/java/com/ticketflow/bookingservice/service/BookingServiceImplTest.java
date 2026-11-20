package com.ticketflow.bookingservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ticketflow.bookingservice.client.CustomerClient;
import com.ticketflow.bookingservice.client.EventClient;
import com.ticketflow.bookingservice.client.dto.CustomerResponse;
import com.ticketflow.bookingservice.client.dto.EventResponse;
import com.ticketflow.bookingservice.client.dto.SeatsRequest;
import com.ticketflow.bookingservice.dto.BookingRequest;
import com.ticketflow.bookingservice.dto.BookingResponse;
import com.ticketflow.bookingservice.entity.Booking;
import com.ticketflow.bookingservice.entity.BookingStatus;
import com.ticketflow.bookingservice.exception.BookingNotFoundException;
import com.ticketflow.bookingservice.exception.CustomerNotFoundException;
import com.ticketflow.bookingservice.exception.EventNotBookableException;
import com.ticketflow.bookingservice.exception.EventNotFoundException;
import com.ticketflow.bookingservice.exception.NotEnoughSeatsException;
import com.ticketflow.bookingservice.exception.RemoteConflictException;
import com.ticketflow.bookingservice.exception.RemoteNotFoundException;
import com.ticketflow.bookingservice.exception.ServiceUnavailableException;
import com.ticketflow.bookingservice.mapper.BookingMapper;
import com.ticketflow.bookingservice.repository.BookingRepository;
import com.ticketflow.bookingservice.service.impl.BookingServiceImpl;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final long CUSTOMER_ID = 7L;
    private static final long EVENT_ID = 42L;
    private static final BookingRequest REQUEST = new BookingRequest(CUSTOMER_ID, EVENT_ID, 3);

    @Mock
    BookingRepository repository;
    @Mock
    EventClient eventClient;
    @Mock
    CustomerClient customerClient;

    BookingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(repository, new BookingMapper(), eventClient, customerClient);
    }

    private static EventResponse event(String status, int availableSeats) {
        return new EventResponse(EVENT_ID, status, availableSeats, new BigDecimal("150.00"));
    }

    private static RetryableException connectionRefused() {
        Request request = Request.create(Request.HttpMethod.GET, "http://event-service/internal/events/42",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request);
    }

    /** What a Feign client throws when its circuit breaker is OPEN (no Feign fallback). */
    private static NoFallbackAvailableException circuitOpen(String service) {
        return new NoFallbackAvailableException("No fallback available.",
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults(service)));
    }

    /** What a Feign client throws when the call itself failed inside the breaker. */
    private static NoFallbackAvailableException wrapped(Throwable cause) {
        return new NoFallbackAvailableException("No fallback available.", cause);
    }

    private void customerExists() {
        when(customerClient.getCustomer(CUSTOMER_ID)).thenReturn(new CustomerResponse(CUSTOMER_ID));
    }

    @Test
    void createReservesSeatsAndSavesBookingWithPriceSnapshot() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenReturn(event("PUBLISHED", 100));
        when(eventClient.reserveSeats(EVENT_ID, new SeatsRequest(3))).thenReturn(event("PUBLISHED", 97));
        when(repository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.create(REQUEST);

        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.quantity()).isEqualTo(3);
        assertThat(response.unitPrice()).isEqualByComparingTo("150.00");
        assertThat(response.totalPrice()).isEqualByComparingTo("450.00");
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(eventClient, never()).releaseSeats(anyLong(), any());
    }

    @Test
    void createFailsWith404WhenCustomerIsMissing() {
        when(customerClient.getCustomer(CUSTOMER_ID)).thenThrow(new RemoteNotFoundException("customer-service"));

        assertThatThrownBy(() -> service.create(REQUEST))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("customer not found");
        verifyNoInteractions(eventClient, repository);
    }

    @Test
    void createFailsWith404WhenEventIsMissing() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenThrow(new RemoteNotFoundException("event-service"));

        assertThatThrownBy(() -> service.create(REQUEST)).isInstanceOf(EventNotFoundException.class);
        verify(eventClient, never()).reserveSeats(anyLong(), any());
        verifyNoInteractions(repository);
    }

    @Test
    void createFailsWith409WhenEventIsNotPublished() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenReturn(event("DRAFT", 100));

        assertThatThrownBy(() -> service.create(REQUEST))
                .isInstanceOf(EventNotBookableException.class)
                .hasMessageContaining("DRAFT");
        verify(eventClient, never()).reserveSeats(anyLong(), any());
        verifyNoInteractions(repository);
    }

    @Test
    void createFailsWith409WhenNotEnoughSeats() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenReturn(event("PUBLISHED", 2));
        when(eventClient.reserveSeats(EVENT_ID, new SeatsRequest(3)))
                .thenThrow(new RemoteConflictException("event-service"));

        assertThatThrownBy(() -> service.create(REQUEST))
                .isInstanceOf(NotEnoughSeatsException.class)
                .hasMessageContaining("not enough seats");
        verifyNoInteractions(repository);
        verify(eventClient, never()).releaseSeats(anyLong(), any());
    }

    @Test
    void createFailsWith503WhenEventServiceIsDown() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenThrow(connectionRefused());

        assertThatThrownBy(() -> service.create(REQUEST))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("event-service");
        verifyNoInteractions(repository);
    }

    @Test
    void createFailsWith503WhenEventServiceAnswers5xx() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenThrow(new ServiceUnavailableException("event-service"));

        assertThatThrownBy(() -> service.create(REQUEST)).isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void createFailsWith503WhenCustomerServiceIsDown() {
        when(customerClient.getCustomer(CUSTOMER_ID)).thenThrow(connectionRefused());

        assertThatThrownBy(() -> service.create(REQUEST))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("customer-service");
        verifyNoInteractions(eventClient, repository);
    }

    @Test
    void createReleasesSeatsWhenSaveFailsAndRethrows() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenReturn(event("PUBLISHED", 100));
        when(eventClient.reserveSeats(EVENT_ID, new SeatsRequest(3))).thenReturn(event("PUBLISHED", 97));
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("db down");
        when(repository.save(any(Booking.class))).thenThrow(dbDown);

        assertThatThrownBy(() -> service.create(REQUEST)).isSameAs(dbDown);
        verify(eventClient).releaseSeats(EVENT_ID, new SeatsRequest(3));
    }

    @Test
    void createKeepsOriginalErrorWhenCompensationAlsoFails() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenReturn(event("PUBLISHED", 100));
        when(eventClient.reserveSeats(EVENT_ID, new SeatsRequest(3))).thenReturn(event("PUBLISHED", 97));
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("db down");
        when(repository.save(any(Booking.class))).thenThrow(dbDown);
        when(eventClient.releaseSeats(eq(EVENT_ID), any())).thenThrow(connectionRefused());

        assertThatThrownBy(() -> service.create(REQUEST))
                .isSameAs(dbDown)
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }

    @Test
    void businessAnswersWrappedByTheCircuitBreakerKeepTheirMeaning() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenThrow(wrapped(new RemoteNotFoundException("event-service")));
        assertThatThrownBy(() -> service.create(REQUEST)).isInstanceOf(EventNotFoundException.class);

        // doReturn: when(...) would invoke the stub, which now throws.
        doReturn(event("PUBLISHED", 2)).when(eventClient).getEvent(EVENT_ID);
        when(eventClient.reserveSeats(EVENT_ID, new SeatsRequest(3)))
                .thenThrow(wrapped(new RemoteConflictException("event-service")));
        assertThatThrownBy(() -> service.create(REQUEST)).isInstanceOf(NotEnoughSeatsException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void technicalFailuresWrappedByTheCircuitBreakerAre503() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenThrow(wrapped(connectionRefused()))
                .thenThrow(wrapped(new java.util.concurrent.TimeoutException("time limiter")))
                .thenThrow(wrapped(new ServiceUnavailableException("event-service")));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.create(REQUEST))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessage("event-service is unavailable, try again later");
        }
        verifyNoInteractions(repository);
    }

    @Test
    void openCircuitIs503AndNothingIsReservedOrSaved() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenThrow(circuitOpen("event-service"));

        assertThatThrownBy(() -> service.create(REQUEST))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("event-service is unavailable (circuit breaker open), try again later")
                .hasCauseInstanceOf(CallNotPermittedException.class);
        verify(eventClient, never()).reserveSeats(anyLong(), any());
        verifyNoInteractions(repository);
    }

    @Test
    void openCustomerCircuitIs503AndEventServiceIsNotCalled() {
        when(customerClient.getCustomer(CUSTOMER_ID)).thenThrow(circuitOpen("customer-service"));

        assertThatThrownBy(() -> service.create(REQUEST))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("customer-service is unavailable (circuit breaker open), try again later");
        verifyNoInteractions(eventClient, repository);
    }

    @Test
    void compensationRejectedByOpenCircuitKeepsOriginalError() {
        customerExists();
        when(eventClient.getEvent(EVENT_ID)).thenReturn(event("PUBLISHED", 100));
        when(eventClient.reserveSeats(EVENT_ID, new SeatsRequest(3))).thenReturn(event("PUBLISHED", 97));
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("db down");
        when(repository.save(any(Booking.class))).thenThrow(dbDown);
        when(eventClient.releaseSeats(eq(EVENT_ID), any())).thenThrow(circuitOpen("event-service"));

        assertThatThrownBy(() -> service.create(REQUEST))
                .isSameAs(dbDown)
                .satisfies(e -> assertThat(e.getSuppressed()).singleElement()
                        .isInstanceOf(ServiceUnavailableException.class)
                        .extracting(Throwable::getCause)
                        .isInstanceOf(CallNotPermittedException.class));
    }

    @Test
    void getUnknownBookingThrowsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(BookingNotFoundException.class);
    }
}
