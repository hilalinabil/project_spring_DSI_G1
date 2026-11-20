package com.ticketflow.notificationservice.listener;

import com.ticketflow.notificationservice.dto.BookingCreatedEvent;
import com.ticketflow.notificationservice.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * One listener container, 3 threads (one per partition of the topic). Messages
 * of one booking share a key, hence a partition, hence a thread: in order.
 * Values are JSON strings (no Spring type headers), parsed here.
 */
@Component
public class BookingCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCreatedListener.class);

    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;

    public BookingCreatedListener(NotificationService notificationService, JsonMapper jsonMapper) {
        this.notificationService = notificationService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${ticketflow.kafka.topics.booking-created}",
            groupId = "notification-service", concurrency = "3")
    public void onBookingCreated(ConsumerRecord<String, String> record) {
        BookingCreatedEvent event;
        try {
            event = jsonMapper.readValue(record.value(), BookingCreatedEvent.class);
        } catch (JacksonException e) {
            // A malformed message will never parse: retrying it would block the
            // partition. Log where it is and move on (a dead-letter topic is the
            // planned improvement).
            log.error("Unreadable message skipped: topic={} partition={} offset={} key={}: {}",
                    record.topic(), record.partition(), record.offset(), record.key(), e.getOriginalMessage());
            return;
        }
        if (event.eventId() == null || event.payload() == null || event.payload().bookingId() == null) {
            log.error("Invalid {} skipped (missing eventId/payload): partition={} offset={} key={}",
                    event.type(), record.partition(), record.offset(), record.key());
            return;
        }
        log.debug("Received {} partition={} offset={} key={}", event.type(), record.partition(),
                record.offset(), record.key());
        notificationService.onBookingCreated(event);
    }
}
