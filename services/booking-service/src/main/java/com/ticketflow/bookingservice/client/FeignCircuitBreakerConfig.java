package com.ticketflow.bookingservice.client;

import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ONE circuit breaker per Feign client ("event-service", "customer-service"),
 * not one per method.
 * <p>
 * spring.cloud.openfeign.circuitbreaker.group.enabled alone is not enough: in
 * Spring Cloud OpenFeign 5.0.x the breaker id still comes from this resolver
 * (default: one per method, e.g. "EventClientgetEventLong"); the group (the
 * Feign client name) only selects a shared config and thread pool. With
 * per-method breakers, 5 failed GETs would open "getEvent" while
 * "reserveSeats" and "releaseSeats" would keep calling a dead service.
 * <p>
 * Returning the client name makes the breaker id equal to the group name, so
 * resilience4j.circuitbreaker.instances.event-service / customer-service in
 * the config repo apply, and each dependency trips independently.
 */
@Configuration(proxyBeanMethods = false)
public class FeignCircuitBreakerConfig {

    @Bean
    CircuitBreakerNameResolver perClientCircuitBreakerNameResolver() {
        return (feignClientName, target, method) -> feignClientName;
    }
}
