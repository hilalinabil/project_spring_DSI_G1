package com.ticketflow.bookingservice.exception;

import java.time.Instant;

/** Every error body: no stack traces, no internal details. */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp) {
}
