package com.ticketflow.eventservice.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long id) {
        super("event " + id + " not found");
    }
}
