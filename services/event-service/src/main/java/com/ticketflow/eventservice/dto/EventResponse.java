package com.ticketflow.eventservice.dto;

import com.ticketflow.eventservice.entity.EventStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** Public representation of an event. */
public record EventResponse(
        Long id,
        String title,
        String description,
        String venue,
        String city,
        LocalDateTime startDateTime,
        BigDecimal price,
        int capacity,
        int availableSeats,
        EventStatus status,
        Instant createdAt) {
}
