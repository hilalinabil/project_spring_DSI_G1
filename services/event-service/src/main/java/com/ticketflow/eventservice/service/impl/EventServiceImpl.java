package com.ticketflow.eventservice.service.impl;

import com.ticketflow.eventservice.dto.EventRequest;
import com.ticketflow.eventservice.dto.EventResponse;
import com.ticketflow.eventservice.dto.InternalEventResponse;
import com.ticketflow.eventservice.dto.PageResponse;
import com.ticketflow.eventservice.entity.Event;
import com.ticketflow.eventservice.entity.EventStatus;
import com.ticketflow.eventservice.exception.EventNotFoundException;
import com.ticketflow.eventservice.exception.InvalidEventStateException;
import com.ticketflow.eventservice.exception.NotEnoughSeatsException;
import com.ticketflow.eventservice.mapper.EventMapper;
import com.ticketflow.eventservice.repository.EventRepository;
import com.ticketflow.eventservice.service.EventService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventServiceImpl implements EventService {

    private final EventRepository repository;
    private final EventMapper mapper;

    public EventServiceImpl(EventRepository repository, EventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public EventResponse create(EventRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listPublished(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("startDateTime", "id"));
        return mapper.toPageResponse(repository.findByStatus(EventStatus.PUBLISHED, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getPublished(Long id) {
        return repository.findByIdAndStatus(id, EventStatus.PUBLISHED)
                .map(mapper::toResponse)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    @Override
    public EventResponse publish(Long id) {
        Event event = find(id);
        event.publish();
        return mapper.toResponse(repository.saveAndFlush(event));
    }

    @Override
    public EventResponse close(Long id) {
        Event event = find(id);
        event.close();
        return mapper.toResponse(repository.saveAndFlush(event));
    }

    @Override
    @Transactional(readOnly = true)
    public InternalEventResponse getInternal(Long id) {
        return mapper.toInternalResponse(find(id));
    }

    @Override
    public InternalEventResponse reserveSeats(Long id, int quantity) {
        if (repository.reserveSeats(id, quantity) == 1) {
            return mapper.toInternalResponse(find(id));
        }
        // 0 rows updated: explain why (the UPDATE itself already decided, nothing changed).
        Event event = find(id);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new InvalidEventStateException(
                    "event " + id + " is " + event.getStatus() + ", seats can only be reserved on a PUBLISHED event");
        }
        throw new NotEnoughSeatsException(id, quantity, event.getAvailableSeats());
    }

    @Override
    public InternalEventResponse releaseSeats(Long id, int quantity) {
        if (repository.releaseSeats(id, quantity) == 0) {
            throw new EventNotFoundException(id);
        }
        return mapper.toInternalResponse(find(id));
    }

    private Event find(Long id) {
        return repository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }
}
