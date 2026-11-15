package com.ticketflow.eventservice.service;

import com.ticketflow.eventservice.dto.EventRequest;
import com.ticketflow.eventservice.dto.EventResponse;
import com.ticketflow.eventservice.dto.InternalEventResponse;
import com.ticketflow.eventservice.dto.PageResponse;

public interface EventService {

    /** Creates a DRAFT event with availableSeats = capacity. */
    EventResponse create(EventRequest request);

    /** PUBLISHED events only. */
    PageResponse<EventResponse> listPublished(int page, int size);

    /** @throws com.ticketflow.eventservice.exception.EventNotFoundException if missing or not PUBLISHED */
    EventResponse getPublished(Long id);

    /** DRAFT -> PUBLISHED, otherwise 409. */
    EventResponse publish(Long id);

    /** PUBLISHED -> CLOSED, otherwise 409. */
    EventResponse close(Long id);

    /** Internal view of any event, whatever its status (404 if unknown). */
    InternalEventResponse getInternal(Long id);

    /** Atomically reserves seats: 404 unknown event, 409 not PUBLISHED or not enough seats. */
    InternalEventResponse reserveSeats(Long id, int quantity);

    /** Gives seats back (compensation), never above capacity: 404 unknown event. */
    InternalEventResponse releaseSeats(Long id, int quantity);
}
