package com.ticketflow.bookingservice.client;

import com.ticketflow.bookingservice.exception.RemoteConflictException;
import com.ticketflow.bookingservice.exception.RemoteNotFoundException;
import com.ticketflow.bookingservice.exception.ServiceUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

/**
 * Turns every non-2xx answer of a remote service into an exception, so a remote
 * error can never be read as a success. Registered as a bean: Spring Cloud
 * OpenFeign uses it for every Feign client.
 * <ul>
 *   <li>404 -> {@link RemoteNotFoundException}</li>
 *   <li>409 -> {@link RemoteConflictException}</li>
 *   <li>5xx (incl. the 503 the load balancer returns when no instance is
 *       registered) -> {@link ServiceUnavailableException}</li>
 *   <li>anything else (e.g. a 400: a bug in this client) -> IllegalStateException (500)</li>
 * </ul>
 * Connection errors and timeouts never reach a decoder: Feign throws a
 * RetryableException, translated in the service layer.
 */
@Component
public class FeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        String service = serviceName(response, methodKey);
        int status = response.status();
        if (status == 404) {
            return new RemoteNotFoundException(service);
        }
        if (status == 409) {
            return new RemoteConflictException(service);
        }
        if (status >= 500) {
            return new ServiceUnavailableException(service);
        }
        return new IllegalStateException(service + " answered HTTP " + status + " to " + methodKey);
    }

    private static String serviceName(Response response, String methodKey) {
        if (response.request() != null && response.request().requestTemplate() != null
                && response.request().requestTemplate().feignTarget() != null) {
            return response.request().requestTemplate().feignTarget().name();
        }
        return methodKey;
    }
}
