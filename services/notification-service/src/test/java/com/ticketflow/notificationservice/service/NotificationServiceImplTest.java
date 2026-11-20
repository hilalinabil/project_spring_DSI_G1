package com.ticketflow.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.notificationservice.dto.BookingCreatedEvent;
import com.ticketflow.notificationservice.service.impl.NotificationServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceImplTest {

    private final NotificationServiceImpl service = new NotificationServiceImpl();

    private static BookingCreatedEvent event(UUID messageId, long bookingId) {
        return new BookingCreatedEvent(messageId, "BookingCreated", 1, Instant.now(),
                new BookingCreatedEvent.Payload(bookingId, 4L, 1L, 2, new BigDecimal("200.00")));
    }

    @Test
    void sameMessageDeliveredTwiceIsProcessedOnce() {
        BookingCreatedEvent event = event(UUID.randomUUID(), 7L);

        assertThat(service.onBookingCreated(event)).isTrue();
        assertThat(service.onBookingCreated(event)).isFalse();
    }

    @Test
    void differentMessagesAreAllProcessedEvenForTheSameBooking() {
        assertThat(service.onBookingCreated(event(UUID.randomUUID(), 7L))).isTrue();
        assertThat(service.onBookingCreated(event(UUID.randomUUID(), 7L))).isTrue();
    }
}
