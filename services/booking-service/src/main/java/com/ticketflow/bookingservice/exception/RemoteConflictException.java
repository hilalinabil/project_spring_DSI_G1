package com.ticketflow.bookingservice.exception;

/** A remote service answered 409. The service layer turns it into a domain exception. */
public class RemoteConflictException extends RuntimeException {

    public RemoteConflictException(String service) {
        super(service + " answered 409");
    }
}
