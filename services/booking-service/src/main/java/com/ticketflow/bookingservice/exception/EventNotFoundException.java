package com.ticketflow.bookingservice.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long id) {
        super("event not found: " + id);
    }
}
