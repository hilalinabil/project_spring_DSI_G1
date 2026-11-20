package com.ticketflow.bookingservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketflow.bookingservice.dto.BookingCreatedEvent;
import com.ticketflow.bookingservice.service.impl.KafkaBookingEventPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class KafkaBookingEventPublisherTest {

    private static final String TOPIC = "ticketflow.booking.created";
    private static final UUID MESSAGE_ID = UUID.fromString("0b6f6c56-1a43-4a8f-9f43-3b8f2e0a6a11");
    private static final BookingCreatedEvent EVENT = new BookingCreatedEvent(MESSAGE_ID, "BookingCreated", 1,
            Instant.parse("2026-11-20T20:00:00.123Z"),
            new BookingCreatedEvent.Payload(7L, 4L, 1L, 2, new BigDecimal("200.00")));

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    final JsonMapper jsonMapper = JsonMapper.builder().build();
    KafkaBookingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaBookingEventPublisher(kafkaTemplate, jsonMapper, TOPIC);
    }

    private static CompletableFuture<SendResult<String, String>> sent() {
        return CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>(TOPIC, "7", "{}"),
                new RecordMetadata(new TopicPartition(TOPIC, 1), 0, 0, 0, 1, 2)));
    }

    @Test
    void sendsJsonKeyedByBookingId() {
        when(kafkaTemplate.send(eq(TOPIC), eq("7"), anyString())).thenReturn(sent());

        publisher.publishCreated(EVENT);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("7"), json.capture());
        JsonNode node = jsonMapper.readTree(json.getValue());
        assertThat(node.get("eventId").asString()).isEqualTo(MESSAGE_ID.toString());
        assertThat(node.get("type").asString()).isEqualTo("BookingCreated");
        assertThat(node.get("version").asInt()).isEqualTo(1);
        assertThat(node.get("occurredAt").asString()).isEqualTo("2026-11-20T20:00:00.123Z");
        JsonNode payload = node.get("payload");
        assertThat(payload.get("bookingId").asLong()).isEqualTo(7);
        assertThat(payload.get("eventId").asLong()).isEqualTo(4);
        assertThat(payload.get("customerId").asLong()).isEqualTo(1);
        assertThat(payload.get("quantity").asInt()).isEqualTo(2);
        assertThat(payload.get("totalPrice").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder(
                "bookingId", "eventId", "customerId", "quantity", "totalPrice");
    }

    @Test
    void asynchronousFailureIsLoggedNotThrown() {
        when(kafkaTemplate.send(eq(TOPIC), eq("7"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker down")));

        assertThatCode(() -> publisher.publishCreated(EVENT)).doesNotThrowAnyException();
    }

    @Test
    void synchronousFailureIsLoggedNotThrown() {
        when(kafkaTemplate.send(eq(TOPIC), eq("7"), anyString()))
                .thenThrow(new org.springframework.kafka.KafkaException("max.block.ms expired"));

        assertThatCode(() -> publisher.publishCreated(EVENT)).doesNotThrowAnyException();
    }
}
