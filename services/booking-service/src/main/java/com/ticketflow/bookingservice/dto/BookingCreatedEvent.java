package com.ticketflow.bookingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message published to ticketflow.booking.created (key = bookingId) once a
 * booking is saved. JSON contract, owned by booking-service:
 * <pre>
 * {"eventId":"&lt;uuid&gt;", "type":"BookingCreated", "version":1,
 *  "occurredAt":"2026-11-20T20:00:00.123Z",
 *  "payload":{"bookingId":7, "eventId":4, "customerId":1, "quantity":2, "totalPrice":200.00}}
 * </pre>
 * The envelope's eventId identifies THIS message (consumers de-duplicate on it);
 * payload.eventId is the booked event. No personal data: ids and amounts only.
 */
public record BookingCreatedEvent(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        Payload payload) {

    public static final String TYPE = "BookingCreated";
    public static final int VERSION = 1;

    public record Payload(
            Long bookingId,
            Long eventId,
            Long customerId,
            int quantity,
            BigDecimal totalPrice) {
    }
}
