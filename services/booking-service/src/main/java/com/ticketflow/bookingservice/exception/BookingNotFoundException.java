package com.ticketflow.bookingservice.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long id) {
        super("booking not found: " + id);
    }
}
