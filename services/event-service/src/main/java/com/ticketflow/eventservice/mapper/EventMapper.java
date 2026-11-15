package com.ticketflow.eventservice.mapper;

import com.ticketflow.eventservice.dto.EventRequest;
import com.ticketflow.eventservice.dto.EventResponse;
import com.ticketflow.eventservice.dto.InternalEventResponse;
import com.ticketflow.eventservice.dto.PageResponse;
import com.ticketflow.eventservice.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Hand-written entity <-> DTO mapping. Entities never leave the service layer. */
@Component
public class EventMapper {

    public Event toEntity(EventRequest request) {
        return new Event(request.title(), request.description(), request.venue(),
                request.city(), request.startDateTime(), request.price(), request.capacity());
    }

    public EventResponse toResponse(Event event) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(),
                event.getVenue(), event.getCity(), event.getStartDateTime(), event.getPrice(),
                event.getCapacity(), event.getAvailableSeats(), event.getStatus(),
                event.getCreatedAt());
    }

    public InternalEventResponse toInternalResponse(Event event) {
        return new InternalEventResponse(event.getId(), event.getStatus(),
                event.getAvailableSeats(), event.getPrice());
    }

    public PageResponse<EventResponse> toPageResponse(Page<Event> page) {
        return new PageResponse<>(page.map(this::toResponse).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
