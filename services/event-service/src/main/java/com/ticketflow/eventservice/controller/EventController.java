package com.ticketflow.eventservice.controller;

import com.ticketflow.eventservice.dto.EventRequest;
import com.ticketflow.eventservice.dto.EventResponse;
import com.ticketflow.eventservice.dto.PageResponse;
import com.ticketflow.eventservice.service.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Public API, routed by the gateway (/api/events/**). */
@RestController
@RequestMapping("/api/events")
public class EventController {

    static final int MAX_PAGE_SIZE = 50;

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        EventResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    PageResponse<EventResponse> listPublished(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return service.listPublished(page, size);
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable Long id) {
        return service.getPublished(id);
    }

    @PatchMapping("/{id}/publish")
    EventResponse publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PatchMapping("/{id}/close")
    EventResponse close(@PathVariable Long id) {
        return service.close(id);
    }
}
