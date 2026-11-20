package com.ticketflow.bookingservice.mapper;

import com.ticketflow.bookingservice.dto.BookingCreatedEvent;
import com.ticketflow.bookingservice.dto.BookingResponse;
import com.ticketflow.bookingservice.dto.PageResponse;
import com.ticketflow.bookingservice.entity.Booking;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Hand-written entity -> DTO mapping. Entities never leave the service layer. */
@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getCustomerId(), booking.getEventId(),
                booking.getQuantity(), booking.getUnitPrice(), booking.getTotalPrice(),
                booking.getStatus(), booking.getCreatedAt());
    }

    /** A new message: fresh UUID, occurredAt = now (Instant: UTC). */
    public BookingCreatedEvent toCreatedEvent(Booking booking) {
        return new BookingCreatedEvent(UUID.randomUUID(), BookingCreatedEvent.TYPE, BookingCreatedEvent.VERSION,
                Instant.now(), new BookingCreatedEvent.Payload(booking.getId(), booking.getEventId(),
                        booking.getCustomerId(), booking.getQuantity(), booking.getTotalPrice()));
    }

    public PageResponse<BookingResponse> toPageResponse(Page<Booking> page) {
        return new PageResponse<>(page.map(this::toResponse).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
