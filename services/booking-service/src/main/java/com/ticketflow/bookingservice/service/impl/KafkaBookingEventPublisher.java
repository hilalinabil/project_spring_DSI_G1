package com.ticketflow.bookingservice.service.impl;

import com.ticketflow.bookingservice.dto.BookingCreatedEvent;
import com.ticketflow.bookingservice.service.BookingEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends the event as a JSON string, key = bookingId (same booking -> same
 * partition -> ordered). Plain String serializers, no Spring type headers:
 * consumers own their copy of the contract and are not tied to our classes.
 * <p>
 * Never blocks the HTTP request on the broker's answer: the result is logged
 * from the send callback. With the broker down, send() waits at most
 * max.block.ms (config repo) for metadata, then fails: logged, booking kept.
 */
@Component
public class KafkaBookingEventPublisher implements BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaBookingEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String topic;

    public KafkaBookingEventPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
                                      @Value("${ticketflow.kafka.topics.booking-created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
    }

    @Override
    public void publishCreated(BookingCreatedEvent event) {
        Long bookingId = event.payload().bookingId();
        try {
            String json = jsonMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, String.valueOf(bookingId), json).whenComplete((result, failure) -> {
                if (failure != null) {
                    logNotPublished(event, failure);
                } else {
                    log.info("{} published: bookingId={} messageId={} topic={} partition={} offset={}",
                            event.type(), bookingId, event.eventId(), topic,
                            result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException failure) {
            logNotPublished(event, failure);
        }
    }

    private void logNotPublished(BookingCreatedEvent event, Throwable failure) {
        log.error("{} NOT published (booking is saved, event lost until the outbox exists): "
                        + "bookingId={} messageId={} topic={}: {}", event.type(), event.payload().bookingId(),
                event.eventId(), topic, NestedExceptionUtils.getMostSpecificCause(failure).toString());
    }
}
