package com.ticketflow.eventservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of POST /internal/events/{id}/reservations and /releases. */
public record SeatsRequest(
        @NotNull @Min(1) @Max(100_000) Integer quantity) {
}
