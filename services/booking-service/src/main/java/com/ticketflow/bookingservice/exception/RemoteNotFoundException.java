package com.ticketflow.bookingservice.exception;

/** A remote service answered 404. The service layer turns it into a domain exception. */
public class RemoteNotFoundException extends RuntimeException {

    public RemoteNotFoundException(String service) {
        super(service + " answered 404");
    }
}
