package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.dto.BookingCreatedEvent;

/**
 * Publishes booking domain events. Best effort: implementations must NEVER
 * throw. A broker outage must not fail a booking that is already saved; the
 * failure is logged instead (the event is then lost: the transactional outbox
 * is the planned fix).
 */
public interface BookingEventPublisher {

    void publishCreated(BookingCreatedEvent event);
}
