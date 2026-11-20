package com.ticketflow.notificationservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * notification-service's own copy of the ticketflow.booking.created contract
 * (owned by booking-service). Unknown JSON properties are ignored, so
 * booking-service can add fields without breaking this consumer.
 * eventId identifies the MESSAGE (de-duplication key); payload.eventId is the
 * booked event.
 */
public record BookingCreatedEvent(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        Payload payload) {

    public record Payload(
            Long bookingId,
            Long eventId,
            Long customerId,
            int quantity,
            BigDecimal totalPrice) {
    }
}
