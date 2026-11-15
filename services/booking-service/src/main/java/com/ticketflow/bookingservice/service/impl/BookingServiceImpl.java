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
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            releaseSeats(eventId, quantity, saveFailure);
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

    /** Compensation: never hides the original failure, even if the release fails too. */
    private void releaseSeats(Long eventId, int quantity, RuntimeException cause) {
        try {
            remote(EVENT_SERVICE, () -> eventClient.releaseSeats(eventId, new SeatsRequest(quantity)));
            log.warn("Booking not saved, released {} seat(s) of event {}", quantity, eventId);
        } catch (RuntimeException releaseFailure) {
            log.error("Booking not saved AND releasing {} seat(s) of event {} failed: seats leaked",
                    quantity, eventId, releaseFailure);
            cause.addSuppressed(releaseFailure);
        }
    }

    /**
     * Connection refused, unknown host, connect or read timeout: Feign throws a
     * RetryableException (never retried: Feign's default Retryer.NEVER_RETRY),
     * which means the dependency is unavailable -> 503.
     */
    private static <T> T remote(String service, Supplier<T> call) {
        try {
            return call.get();
        } catch (RetryableException e) {
            throw new ServiceUnavailableException(service, e);
        }
    }
}
