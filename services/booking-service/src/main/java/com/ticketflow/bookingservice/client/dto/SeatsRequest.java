package com.ticketflow.bookingservice.client.dto;

/** Body of event-service's POST /internal/events/{id}/reservations and /releases. */
public record SeatsRequest(int quantity) {
}
