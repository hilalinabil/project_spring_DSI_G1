package com.ticketflow.bookingservice.service.impl;

import com.ticketflow.bookingservice.client.CustomerClient;
import com.ticketflow.bookingservice.client.EventClient;
import com.ticketflow.bookingservice.client.dto.EventResponse;
import com.ticketflow.bookingservice.client.dto.SeatsRequest;
import com.ticketflow.bookingservice.dto.BookingRequest;
import com.ticketflow.bookingservice.dto.BookingResponse;
import com.ticketflow.bookingservice.dto.PageResponse;
import com.ticketflow.bookingservice.entity.Booking;
import com.ticketflow.bookingservice.exception.BookingNotFoundException;
import com.ticketflow.bookingservice.exception.CustomerNotFoundException;
import com.ticketflow.bookingservice.exception.EventNotBookableException;
import com.ticketflow.bookingservice.exception.EventNotFoundException;
import com.ticketflow.bookingservice.exception.NotEnoughSeatsException;
import com.ticketflow.bookingservice.exception.RemoteConflictException;
import com.ticketflow.bookingservice.exception.RemoteNotFoundException;
import com.ticketflow.bookingservice.exception.ServiceUnavailableException;
import com.ticketflow.bookingservice.mapper.BookingMapper;
import com.ticketflow.bookingservice.repository.BookingRepository;
import com.ticketflow.bookingservice.service.BookingService;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    static final String CUSTOMER_SERVICE = "customer-service";
    static final String EVENT_SERVICE = "event-service";

    private final BookingRepository repository;
    private final BookingMapper mapper;
    private final EventClient eventClient;
    private final CustomerClient customerClient;

    public BookingServiceImpl(BookingRepository repository, BookingMapper mapper,
                              EventClient eventClient, CustomerClient customerClient) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventClient = eventClient;
        this.customerClient = customerClient;
    }

    /**
     * NOT run inside a DB transaction: it makes HTTP calls, and a transaction
     * would hold a DB connection for their whole duration. The only write,
     * repository.save, runs (and commits) in its own transaction, so a failure
     * to persist surfaces right there and can be compensated.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BookingResponse create(BookingRequest request) {
        Long customerId = request.customerId();
        Long eventId = request.eventId();
        int quantity = request.quantity();

        // 1. The customer must exist.
        try {
            remote(CUSTOMER_SERVICE, () -> customerClient.getCustomer(customerId));
        } catch (RemoteNotFoundException e) {
            throw new CustomerNotFoundException(customerId);
        }

        // 2. The event must exist and be PUBLISHED.
        EventResponse event;
        try {
            event = remote(EVENT_SERVICE, () -> eventClient.getEvent(eventId));
        } catch (RemoteNotFoundException e) {
            throw new EventNotFoundException(eventId);
        }
        if (!event.isPublished()) {
            throw new EventNotBookableException(eventId, event.status());
        }

        // 3. Reserve the seats (atomic on event-service's side).
        try {
            remote(EVENT_SERVICE, () -> eventClient.reserveSeats(eventId, new SeatsRequest(quantity)));
        } catch (RemoteConflictException e) {
            // Not PUBLISHED any more (closed in the meantime) or not enough seats.
            throw new NotEnoughSeatsException(eventId, quantity);
        } catch (RemoteNotFoundException e) {
            throw new EventNotFoundException(eventId);
        }

        // 4. Persist with the price snapshot; give the seats back if that fails.
        try {
            Booking saved = repository.save(new Booking(customerId, eventId, quantity, event.price()));
            return mapper.toResponse(saved);
        } catch (RuntimeException saveFailure) {
            releaseSeats(customerId, eventId, quantity, saveFailure);
            throw saveFailure;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listByCustomer(Long customerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return mapper.toPageResponse(repository.findByCustomerId(customerId, pageable));
    }

    /**
     * Compensation: never hides the original failure, even if the release fails too.
     * The booking was NOT saved, so there is no booking id: the log carries
     * customer, event and quantity instead. A release rejected by an OPEN
     * circuit is never sent: the seats stay reserved until released by hand
     * (known limitation, fixed by the future saga).
     */
    private void releaseSeats(Long customerId, Long eventId, int quantity, RuntimeException cause) {
        try {
            remote(EVENT_SERVICE, () -> eventClient.releaseSeats(eventId, new SeatsRequest(quantity)));
            log.warn("Booking not saved, released {} seat(s) of event {}", quantity, eventId);
        } catch (RuntimeException releaseFailure) {
            if (releaseFailure.getCause() instanceof CallNotPermittedException) {
                log.error("COMPENSATION NOT SENT: circuit breaker '{}' is OPEN. Booking not saved "
                                + "(no booking id) for customer {}; {} seat(s) of event {} stay reserved: "
                                + "release them manually", EVENT_SERVICE, customerId, quantity, eventId);
            } else {
                log.error("Booking not saved AND releasing {} seat(s) of event {} (customer {}) failed: "
                        + "seats leaked", quantity, eventId, customerId, releaseFailure);
            }
            cause.addSuppressed(releaseFailure);
        }
    }

    /**
     * Every Feign call runs inside the client's circuit breaker (one per client,
     * see FeignCircuitBreakerConfig). With no Feign fallback, Spring Cloud wraps
     * whatever went wrong in a NoFallbackAvailableException: unwrap it so the
     * callers see our own exceptions, then translate the technical ones to 503.
     * <ul>
     *   <li>CallNotPermittedException: circuit OPEN, no request was sent;</li>
     *   <li>RetryableException: connection refused, unknown host, connect or read
     *       timeout (never retried: Feign's default Retryer.NEVER_RETRY);</li>
     *   <li>TimeoutException: the circuit breaker's time limiter gave up.</li>
     * </ul>
     * RemoteNotFoundException / RemoteConflictException (404 / 409) are rethrown
     * as is: they are business answers, handled by the callers.
     */
    private static <T> T remote(String service, Supplier<T> call) {
        try {
            return call.get();
        } catch (NoFallbackAvailableException e) {
            throw translate(service, e.getCause() != null ? e.getCause() : e);
        } catch (RuntimeException e) {
            throw translate(service, e);
        }
    }

    private static RuntimeException translate(String service, Throwable failure) {
        if (failure instanceof CallNotPermittedException) {
            return ServiceUnavailableException.circuitOpen(service, failure);
        }
        if (failure instanceof RetryableException || failure instanceof TimeoutException) {
            return new ServiceUnavailableException(service, failure);
        }
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(service + " call failed", failure);
    }
}
