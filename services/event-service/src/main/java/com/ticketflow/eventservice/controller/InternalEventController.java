package com.ticketflow.eventservice.controller;

import com.ticketflow.eventservice.dto.InternalEventResponse;
import com.ticketflow.eventservice.dto.SeatsRequest;
import com.ticketflow.eventservice.service.EventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for booking-service only. NOT routed by the gateway: it is
 * reachable only inside the Docker network (service-to-service via Eureka).
 */
@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

    private final EventService service;

    public InternalEventController(EventService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    InternalEventResponse get(@PathVariable Long id) {
        return service.getInternal(id);
    }

    @PostMapping("/{id}/reservations")
    InternalEventResponse reserve(@PathVariable Long id, @Valid @RequestBody SeatsRequest request) {
        return service.reserveSeats(id, request.quantity());
    }

    @PostMapping("/{id}/releases")
    InternalEventResponse release(@PathVariable Long id, @Valid @RequestBody SeatsRequest request) {
        return service.releaseSeats(id, request.quantity());
    }
}
