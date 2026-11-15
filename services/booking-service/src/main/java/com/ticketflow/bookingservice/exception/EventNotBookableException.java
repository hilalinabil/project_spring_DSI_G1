package com.ticketflow.bookingservice.exception;

/** The event exists but is not PUBLISHED (409). */
public class EventNotBookableException extends RuntimeException {

    public EventNotBookableException(Long id, String status) {
        super("event " + id + " is " + status + ", only PUBLISHED events can be booked");
    }
}
