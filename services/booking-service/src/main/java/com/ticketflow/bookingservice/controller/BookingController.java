package com.ticketflow.bookingservice.controller;

import com.ticketflow.bookingservice.dto.BookingRequest;
import com.ticketflow.bookingservice.dto.BookingResponse;
import com.ticketflow.bookingservice.dto.PageResponse;
import com.ticketflow.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Public API, routed by the gateway (/api/bookings/**). */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    static final int MAX_PAGE_SIZE = 50;

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        BookingResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    BookingResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    PageResponse<BookingResponse> listByCustomer(
            @RequestParam @Min(1) Long customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return service.listByCustomer(customerId, page, size);
    }
}
