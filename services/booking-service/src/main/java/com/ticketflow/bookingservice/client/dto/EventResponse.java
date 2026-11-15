package com.ticketflow.bookingservice.client.dto;

import java.math.BigDecimal;

/**
 * What booking-service reads from event-service's GET /internal/events/{id}
 * (and from the reservation response). Owned by booking-service: only the
 * fields it uses, mapped by JSON field name. status stays a String so a new
 * status in event-service cannot break deserialization here.
 */
public record EventResponse(
        Long id,
        String status,
        int availableSeats,
        BigDecimal price) {

    public static final String PUBLISHED = "PUBLISHED";

    public boolean isPublished() {
        return PUBLISHED.equals(status);
    }
}
