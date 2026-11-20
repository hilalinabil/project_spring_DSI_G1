package com.ticketflow.notificationservice.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Real listener container (3 threads) against an embedded KRaft broker:
 * JSON contract, duplicate delivery, malformed message, unknown property.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = "ticketflow.booking.created",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ExtendWith(OutputCaptureExtension.class)
class BookingCreatedListenerTest {

    private static final String TOPIC = "ticketflow.booking.created";

    private static String message(String messageId, long bookingId) {
        return """
                {"eventId":"%s","type":"BookingCreated","version":1,"occurredAt":"2026-11-20T20:00:00.123Z",
                 "payload":{"bookingId":%d,"eventId":4,"customerId":1,"quantity":2,"totalPrice":200.00},
                 "addedLater":"ignored by this consumer"}
                """.formatted(messageId, bookingId);
    }

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void duplicateIsProcessedOnceAndMalformedMessageIsSkipped(CapturedOutput output) throws Exception {
        String duplicated = message("6f1d2c3b-0000-4000-8000-000000000001", 7);
        kafkaTemplate.send(TOPIC, "7", duplicated).get();
        kafkaTemplate.send(TOPIC, "7", duplicated).get();          // redelivery of the same message
        kafkaTemplate.send(TOPIC, "8", "{not json").get();         // poison message
        kafkaTemplate.send(TOPIC, "8", message("6f1d2c3b-0000-4000-8000-000000000002", 8)).get();

        awaitCount(output, "Notification sent: booking 8 created", 1);
        awaitCount(output, "Duplicate BookingCreated skipped", 1);

        assertThat(count(output, "Notification sent: booking 7 created")).isEqualTo(1);
        assertThat(count(output, "Notification sent: booking 8 created")).isEqualTo(1);
        assertThat(count(output, "Unreadable message skipped")).isEqualTo(1);
        // Same key -> same partition: booking 7's duplicate came after its original.
        assertThat(output.getOut().indexOf("Notification sent: booking 7 created"))
                .isLessThan(output.getOut().indexOf("Duplicate BookingCreated skipped"));
    }

    private static void awaitCount(CapturedOutput output, String text, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (count(output, text) < expected && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(count(output, text)).as(text).isEqualTo(expected);
    }

    private static int count(CapturedOutput output, String text) {
        Matcher matcher = Pattern.compile(Pattern.quote(text)).matcher(output.getOut());
        int n = 0;
        while (matcher.find()) {
            n++;
        }
        return n;
    }
}
