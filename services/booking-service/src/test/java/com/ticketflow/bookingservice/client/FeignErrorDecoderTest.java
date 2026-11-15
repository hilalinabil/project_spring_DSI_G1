package com.ticketflow.bookingservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.bookingservice.exception.RemoteConflictException;
import com.ticketflow.bookingservice.exception.RemoteNotFoundException;
import com.ticketflow.bookingservice.exception.ServiceUnavailableException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.Target;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    private static Response response(int status) {
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(new Target.HardCodedTarget<>(EventClient.class, "event-service",
                "http://event-service"));
        Request request = Request.create(Request.HttpMethod.GET, "http://event-service/internal/events/1",
                Map.of(), null, StandardCharsets.UTF_8, template);
        return Response.builder().status(status).reason("x").request(request).headers(Map.of()).build();
    }

    @Test
    void notFoundBecomesRemoteNotFound() {
        assertThat(decoder.decode("EventClient#getEvent(Long)", response(404)))
                .isInstanceOf(RemoteNotFoundException.class)
                .hasMessageContaining("event-service");
    }

    @Test
    void conflictBecomesRemoteConflict() {
        assertThat(decoder.decode("EventClient#reserveSeats(Long,SeatsRequest)", response(409)))
                .isInstanceOf(RemoteConflictException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void serverErrorsBecomeServiceUnavailable(int status) {
        assertThat(decoder.decode("EventClient#getEvent(Long)", response(status)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("event-service");
    }

    @Test
    void otherClientErrorsAreNeverASuccess() {
        assertThat(decoder.decode("EventClient#getEvent(Long)", response(400)))
                .isInstanceOf(IllegalStateException.class);
    }
}
