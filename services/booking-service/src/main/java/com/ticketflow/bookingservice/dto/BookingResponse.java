package com.ticketflow.bookingservice.dto;

import com.ticketflow.bookingservice.entity.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long customerId,
        Long eventId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BookingStatus status,
        Instant createdAt) {
}
