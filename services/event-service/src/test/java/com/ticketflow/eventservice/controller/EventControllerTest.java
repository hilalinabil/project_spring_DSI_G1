package com.ticketflow.eventservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketflow.eventservice.dto.EventResponse;
import com.ticketflow.eventservice.dto.PageResponse;
import com.ticketflow.eventservice.entity.EventStatus;
import com.ticketflow.eventservice.exception.EventNotFoundException;
import com.ticketflow.eventservice.exception.InvalidEventStateException;
import com.ticketflow.eventservice.service.EventService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService service;

    private static EventResponse response(Long id, EventStatus status) {
        return new EventResponse(id, "Concert", "desc", "Arena", "Rabat",
                LocalDateTime.of(2030, 1, 1, 20, 0), new BigDecimal("150.00"), 500, 500,
                status, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static final String VALID_BODY = """
            {"title":"Concert","description":"desc","venue":"Arena","city":"Rabat",
             "startDateTime":"2030-01-01T20:00:00","price":150.00,"capacity":500}
            """;

    @Test
    void createReturns201WithLocation() throws Exception {
        when(service.create(any())).thenReturn(response(42L, EventStatus.DRAFT));

        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/events/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.availableSeats").value(500));
    }

    @Test
    void createWithInvalidBodyReturns400WithStructuredError() throws Exception {
        String body = """
                {"title":"","venue":"Arena","city":"Rabat",
                 "startDateTime":"2000-01-01T20:00:00","price":-1,"capacity":0}
                """;

        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("capacity")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("startDateTime")))
                .andExpect(jsonPath("$.path").value("/api/events"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("malformed JSON request body"));
    }

    @Test
    void listReturnsAPage() throws Exception {
        when(service.listPublished(0, 20)).thenReturn(
                new PageResponse<>(List.of(response(1L, EventStatus.PUBLISHED)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pageSizeIsBounded() throws Exception {
        mockMvc.perform(get("/api/events").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("size")));
        verifyNoInteractions(service);
    }

    @Test
    void getMissingOrDraftEventReturns404() throws Exception {
        when(service.getPublished(5L)).thenThrow(new EventNotFoundException(5L));

        mockMvc.perform(get("/api/events/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("event 5 not found"))
                .andExpect(jsonPath("$.path").value("/api/events/5"));
    }

    @Test
    void publishReturnsThePublishedEvent() throws Exception {
        when(service.publish(1L)).thenReturn(response(1L, EventStatus.PUBLISHED));

        mockMvc.perform(patch("/api/events/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void invalidTransitionReturns409() throws Exception {
        when(service.close(1L)).thenThrow(
                new InvalidEventStateException(1L, EventStatus.DRAFT, EventStatus.CLOSED));

        mockMvc.perform(patch("/api/events/1/close"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/events/abc"))
                .andExpect(status().isBadRequest());
    }
}
