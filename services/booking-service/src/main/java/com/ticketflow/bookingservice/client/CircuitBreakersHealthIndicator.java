package com.ticketflow.bookingservice.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Shows each breaker's state under /actuator/health -> components.circuitBreakers.
 * <p>
 * Needed because resilience4j-spring-boot3 2.3.0 ships its health indicator
 * against the Boot 3 package (org.springframework.boot.actuate.health), which
 * no longer exists in Boot 4: its auto-configuration is silently skipped.
 * <p>
 * Always UP: an OPEN breaker means a DEPENDENCY is down, not booking-service.
 * Reporting DOWN would turn the Docker healthcheck red and cascade the outage
 * to a service that still answers (fast 503s, reads of existing bookings).
 */
@Component("circuitBreakers")
public class CircuitBreakersHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry registry;

    public CircuitBreakersHealthIndicator(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        registry.getAllCircuitBreakers().stream()
                .sorted(Comparator.comparing(CircuitBreaker::getName))
                .forEach(cb -> builder.withDetail(cb.getName(), details(cb)));
        return builder.build();
    }

    private static Map<String, Object> details(CircuitBreaker cb) {
        CircuitBreaker.Metrics metrics = cb.getMetrics();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("state", cb.getState().name());
        details.put("failureRate", metrics.getFailureRate() + "%");
        details.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
        details.put("failedCalls", metrics.getNumberOfFailedCalls());
        details.put("notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
        return details;
    }
}
