package com.ticketflow.bookingservice.client.dto;

/**
 * What booking-service reads from customer-service's GET /api/customers/{id}
 * ({"id", "firstName", "lastName", "email", "phone", "createdAt"}). Only the
 * field it uses: the other properties of the JSON are ignored.
 */
public record CustomerResponse(Long id) {
}
