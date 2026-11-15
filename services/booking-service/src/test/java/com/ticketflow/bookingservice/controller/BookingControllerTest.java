package com.ticketflow.bookingservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketflow.bookingservice.dto.BookingResponse;
import com.ticketflow.bookingservice.dto.PageResponse;
import com.ticketflow.bookingservice.entity.BookingStatus;
import com.ticketflow.bookingservice.exception.BookingNotFoundException;
import com.ticketflow.bookingservice.exception.CustomerNotFoundException;
import com.ticketflow.bookingservice.exception.EventNotBookableException;
import com.ticketflow.bookingservice.exception.NotEnoughSeatsException;
import com.ticketflow.bookingservice.exception.ServiceUnavailableException;
import com.ticketflow.bookingservice.service.BookingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookingService service;

    private static final String VALID_BODY = """
            {"customerId":7,"eventId":42,"quantity":3}
            """;

    private static BookingResponse response(Long id) {
        return new BookingResponse(id, 7L, 42L, 3, new BigDecimal("150.00"), new BigDecimal("450.00"),
                BookingStatus.PENDING_PAYMENT, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(service.create(any())).thenReturn(response(5L));

        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/bookings/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.totalPrice").value(450.00))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void createRejectsQuantityOutOfRange() throws Exception {
        for (String quantity : List.of("0", "11")) {
            mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":7,\"eventId\":42,\"quantity\":" + quantity + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("quantity")))
                    .andExpect(jsonPath("$.path").value("/api/bookings"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
        verifyNoInteractions(service);
    }

    @Test
    void createRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unknownCustomerIs404() throws Exception {
        when(service.create(any())).thenThrow(new CustomerNotFoundException(7L));

        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("customer not found: 7"));
    }

    @Test
    void businessConflictsAre409() throws Exception {
        when(service.create(any())).thenThrow(new EventNotBookableException(42L, "DRAFT"))
                .thenThrow(new NotEnoughSeatsException(42L, 3));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("Conflict"));
        }
    }

    @Test
    void dependencyDownIs503WithoutStackTrace() throws Exception {
        when(service.create(any())).thenThrow(new ServiceUnavailableException("event-service",
                new RuntimeException("Connection refused")));

        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("event-service is unavailable, try again later"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void getUnknownBookingIs404() throws Exception {
        when(service.get(99L)).thenThrow(new BookingNotFoundException(99L));

        mockMvc.perform(get("/api/bookings/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listByCustomerIsPaginatedAndBounded() throws Exception {
        when(service.listByCustomer(7L, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response(5L)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/bookings").param("customerId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/bookings").param("customerId", "7").param("size", "51"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("customerId: is required"));
    }
}
