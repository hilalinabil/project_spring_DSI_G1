package com.ticketflow.bookingservice.exception;

public class NotEnoughSeatsException extends RuntimeException {

    public NotEnoughSeatsException(Long eventId, int requested) {
        super("not enough seats for event " + eventId + ": requested " + requested);
    }
}
