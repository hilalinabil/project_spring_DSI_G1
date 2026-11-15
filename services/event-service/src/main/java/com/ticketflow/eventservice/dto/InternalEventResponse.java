package com.ticketflow.eventservice.dto;

import com.ticketflow.eventservice.entity.EventStatus;
import java.math.BigDecimal;

/** Internal API (booking-service only): just what a booking needs. */
public record InternalEventResponse(
        Long id,
        EventStatus status,
        int availableSeats,
        BigDecimal price) {
}
