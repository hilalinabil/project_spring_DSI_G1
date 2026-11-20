package com.ticketflow.bookingservice.exception;

/** A dependency is down, unreachable, too slow, failing, or its circuit is OPEN (503). */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String service) {
        super(service + " is unavailable, try again later");
    }

    public ServiceUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable, try again later", cause);
    }

    private ServiceUnavailableException(String message, Throwable cause, boolean ignored) {
        super(message, cause);
    }

    /** The call was rejected by an OPEN circuit breaker: no request was sent. */
    public static ServiceUnavailableException circuitOpen(String service, Throwable cause) {
        return new ServiceUnavailableException(
                service + " is unavailable (circuit breaker open), try again later", cause, true);
    }
}
