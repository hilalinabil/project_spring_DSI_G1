package com.ticketflow.notificationservice.service;

import com.ticketflow.notificationservice.dto.BookingCreatedEvent;

public interface NotificationService {

    /**
     * Sends the "booking created" notification, at most once per message id.
     *
     * @return true if sent, false if this message was already processed (duplicate)
     */
    boolean onBookingCreated(BookingCreatedEvent event);
}
