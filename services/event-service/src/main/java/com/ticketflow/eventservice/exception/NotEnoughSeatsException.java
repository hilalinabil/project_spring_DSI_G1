package com.ticketflow.eventservice.exception;

public class NotEnoughSeatsException extends RuntimeException {

    public NotEnoughSeatsException(Long id, int requested, int available) {
        super("not enough seats for event " + id + ": requested " + requested
                + ", available " + available);
    }
}
