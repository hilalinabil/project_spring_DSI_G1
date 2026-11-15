package com.ticketflow.bookingservice.client;

import com.ticketflow.bookingservice.client.dto.EventResponse;
import com.ticketflow.bookingservice.client.dto.SeatsRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * event-service's internal API. No url attribute: "event-service" is resolved
 * through Eureka + Spring Cloud LoadBalancer, never through the gateway.
 * Remote errors are translated by {@link FeignErrorDecoder}.
 */
@FeignClient(name = "event-service")
public interface EventClient {

    @GetMapping("/internal/events/{id}")
    EventResponse getEvent(@PathVariable("id") Long id);

    @PostMapping("/internal/events/{id}/reservations")
    EventResponse reserveSeats(@PathVariable("id") Long id, @RequestBody SeatsRequest request);

    @PostMapping("/internal/events/{id}/releases")
    EventResponse releaseSeats(@PathVariable("id") Long id, @RequestBody SeatsRequest request);
}
