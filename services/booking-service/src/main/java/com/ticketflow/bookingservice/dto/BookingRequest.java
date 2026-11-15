package com.ticketflow.bookingservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of POST /api/bookings. customerId in the body is temporary: it will come
 * from the JWT once Keycloak is in place.
 */
public record BookingRequest(
        @NotNull @Positive Long customerId,
        @NotNull @Positive Long eventId,
        @NotNull @Min(1) @Max(10) Integer quantity) {
}
