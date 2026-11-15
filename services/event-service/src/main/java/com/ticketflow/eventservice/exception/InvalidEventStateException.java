package com.ticketflow.eventservice.exception;

import com.ticketflow.eventservice.entity.EventStatus;

/** A status transition or operation that the event's current status does not allow (409). */
public class InvalidEventStateException extends RuntimeException {

    public InvalidEventStateException(Long id, EventStatus current, EventStatus target) {
        super("event " + id + " cannot go from " + current + " to " + target);
    }

    public InvalidEventStateException(String message) {
        super(message);
    }
}
