package com.ticketflow.eventservice.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketflow.eventservice.dto.InternalEventResponse;
import com.ticketflow.eventservice.entity.EventStatus;
import com.ticketflow.eventservice.exception.EventNotFoundException;
import com.ticketflow.eventservice.exception.NotEnoughSeatsException;
import com.ticketflow.eventservice.service.EventService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalEventController.class)
class InternalEventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService service;

    @Test
    void getReturnsOnlyTheInternalFields() throws Exception {
        when(service.getInternal(1L)).thenReturn(
                new InternalEventResponse(1L, EventStatus.PUBLISHED, 8, new BigDecimal("150.00")));

        mockMvc.perform(get("/internal/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.availableSeats").value(8))
                .andExpect(jsonPath("$.price").value(150.00))
                .andExpect(jsonPath("$.title").doesNotExist());
    }

    @Test
    void reserveReturns200() throws Exception {
        when(service.reserveSeats(1L, 2)).thenReturn(
                new InternalEventResponse(1L, EventStatus.PUBLISHED, 6, new BigDecimal("150.00")));

        mockMvc.perform(post("/internal/events/1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(6));
    }

    @Test
    void reserveWithoutEnoughSeatsReturns409() throws Exception {
        when(service.reserveSeats(1L, 9)).thenThrow(new NotEnoughSeatsException(1L, 9, 8));

        mockMvc.perform(post("/internal/events/1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void reserveOnUnknownEventReturns404() throws Exception {
        when(service.reserveSeats(99L, 1)).thenThrow(new EventNotFoundException(99L));

        mockMvc.perform(post("/internal/events/99/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserveWithInvalidQuantityReturns400() throws Exception {
        mockMvc.perform(post("/internal/events/1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void releaseReturns200() throws Exception {
        when(service.releaseSeats(1L, 2)).thenReturn(
                new InternalEventResponse(1L, EventStatus.PUBLISHED, 10, new BigDecimal("150.00")));

        mockMvc.perform(post("/internal/events/1/releases")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(10));
    }
}
