package com.ticketflow.notificationservice.service.impl;

import com.ticketflow.notificationservice.dto.BookingCreatedEvent;
import com.ticketflow.notificationservice.service.NotificationService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Kafka delivers at least once (offsets are committed after processing, so a
 * crash or a rebalance can redeliver a message): de-duplicate on the message id.
 * <p>
 * Limitation: the processed ids live in memory, per instance, bounded to the
 * last {@value #MAX_REMEMBERED} ids. A restart, a second instance or a very old
 * redelivery can still send a duplicate. The durable fix is a processed-events
 * table written in the same transaction as the notification.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    static final int MAX_REMEMBERED = 10_000;

    /** Insertion-ordered, oldest evicted first; synchronized: 3 listener threads. */
    private final Set<UUID> processed = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > MAX_REMEMBERED;
                }
            }));

    @Override
    public boolean onBookingCreated(BookingCreatedEvent event) {
        if (processed.contains(event.eventId())) {
            log.info("Duplicate {} skipped: messageId={} bookingId={}", event.type(), event.eventId(),
                    event.payload().bookingId());
            return false;
        }
        // Simulated channel: a log line. Ids only, no personal data.
        log.info("Notification sent: booking {} created (customer {}, event {}, {} seat(s), total {}) "
                        + "messageId={}", event.payload().bookingId(), event.payload().customerId(),
                event.payload().eventId(), event.payload().quantity(), event.payload().totalPrice(),
                event.eventId());
        // Marked only once sent: if sending throws, the redelivery is not skipped.
        processed.add(event.eventId());
        return true;
    }
}
