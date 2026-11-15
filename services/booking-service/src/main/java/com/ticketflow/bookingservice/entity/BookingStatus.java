package com.ticketflow.bookingservice.entity;

/** Only PENDING_PAYMENT is used for now: there is no payment service yet. */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED
}
