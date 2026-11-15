package com.ticketflow.bookingservice.exception;

/** A dependency is down, unreachable, too slow or failing (503). */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String service) {
        super(service + " is unavailable, try again later");
    }

    public ServiceUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable, try again later", cause);
    }
}
