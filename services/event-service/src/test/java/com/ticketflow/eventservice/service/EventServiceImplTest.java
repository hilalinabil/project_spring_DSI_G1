package com.ticketflow.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketflow.eventservice.dto.EventRequest;
import com.ticketflow.eventservice.dto.EventResponse;
import com.ticketflow.eventservice.dto.InternalEventResponse;
import com.ticketflow.eventservice.entity.Event;
import com.ticketflow.eventservice.entity.EventStatus;
import com.ticketflow.eventservice.exception.EventNotFoundException;
import com.ticketflow.eventservice.exception.InvalidEventStateException;
import com.ticketflow.eventservice.exception.NotEnoughSeatsException;
import com.ticketflow.eventservice.mapper.EventMapper;
import com.ticketflow.eventservice.repository.EventRepository;
import com.ticketflow.eventservice.service.impl.EventServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    EventRepository repository;

    EventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EventServiceImpl(repository, new EventMapper());
    }

    private static Event draft(int capacity) {
        return new Event("Concert", "desc", "Arena", "Rabat",
                LocalDateTime.now().plusDays(10), new BigDecimal("150.00"), capacity);
    }

    private static Event published(int capacity) {
        Event event = draft(capacity);
        event.publish();
        return event;
    }

    @Test
    void createStartsAsDraftWithAllSeatsAvailable() {
        when(repository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        EventRequest request = new EventRequest("Concert", "desc", "Arena", "Rabat",
                LocalDateTime.now().plusDays(10), new BigDecimal("150.00"), 500);

        EventResponse response = service.create(request);

        assertThat(response.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(response.capacity()).isEqualTo(500);
        assertThat(response.availableSeats()).isEqualTo(500);
    }

    @Test
    void getPublishedThrowsNotFoundForMissingOrNonPublishedEvent() {
        when(repository.findByIdAndStatus(7L, EventStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublished(7L)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void publishMovesDraftToPublished() {
        Event event = draft(10);
        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(repository.saveAndFlush(event)).thenReturn(event);

        assertThat(service.publish(1L).status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publishTwiceIsAConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(published(10)));

        assertThatThrownBy(() -> service.publish(1L)).isInstanceOf(InvalidEventStateException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void closeMovesPublishedToClosed() {
        Event event = published(10);
        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(repository.saveAndFlush(event)).thenReturn(event);

        assertThat(service.close(1L).status()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    void closingADraftIsAConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(draft(10)));

        assertThatThrownBy(() -> service.close(1L)).isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void publishUnknownEventIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(99L)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void reserveSucceedsWhenTheConditionalUpdateHitsOneRow() {
        when(repository.reserveSeats(1L, 3)).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(published(10)));

        InternalEventResponse response = service.reserveSeats(1L, 3);

        assertThat(response.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void reserveOnUnknownEventIsNotFound() {
        when(repository.reserveSeats(99L, 1)).thenReturn(0);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveSeats(99L, 1)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void reserveOnDraftEventIsAConflict() {
        when(repository.reserveSeats(1L, 1)).thenReturn(0);
        when(repository.findById(1L)).thenReturn(Optional.of(draft(10)));

        assertThatThrownBy(() -> service.reserveSeats(1L, 1)).isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void reserveMoreThanAvailableIsNotEnoughSeats() {
        when(repository.reserveSeats(1L, 11)).thenReturn(0);
        when(repository.findById(1L)).thenReturn(Optional.of(published(10)));

        assertThatThrownBy(() -> service.reserveSeats(1L, 11))
                .isInstanceOf(NotEnoughSeatsException.class)
                .hasMessageContaining("requested 11, available 10");
    }

    @Test
    void releaseOnUnknownEventIsNotFound() {
        when(repository.releaseSeats(99L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.releaseSeats(99L, 2)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void releaseReturnsTheUpdatedEvent() {
        when(repository.releaseSeats(1L, 2)).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(published(10)));

        assertThat(service.releaseSeats(1L, 2).availableSeats()).isEqualTo(10);
    }
}
