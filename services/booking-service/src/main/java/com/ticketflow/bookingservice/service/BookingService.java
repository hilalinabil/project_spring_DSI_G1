package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.dto.BookingRequest;
import com.ticketflow.bookingservice.dto.BookingResponse;
import com.ticketflow.bookingservice.dto.PageResponse;

public interface BookingService {

    /**
     * Books seats: the customer must exist (404), the event must exist (404) and be
     * PUBLISHED (409), and enough seats must be left (409). 503 if a dependency is down.
     */
    BookingResponse create(BookingRequest request);

    /** @throws com.ticketflow.bookingservice.exception.BookingNotFoundException if missing */
    BookingResponse get(Long id);

    /** Bookings of one customer, newest first. */
    PageResponse<BookingResponse> listByCustomer(Long customerId, int page, int size);
}
