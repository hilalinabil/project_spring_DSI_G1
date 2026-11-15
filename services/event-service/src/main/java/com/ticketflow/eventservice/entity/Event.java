package com.ticketflow.eventservice.entity;

import com.ticketflow.eventservice.exception.InvalidEventStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Event() {
        // required by JPA
    }

    /** A new event starts as DRAFT with every seat available. */
    public Event(String title, String description, String venue, String city,
                 LocalDateTime startDateTime, BigDecimal price, int capacity) {
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.city = city;
        this.startDateTime = startDateTime;
        this.price = price;
        this.capacity = capacity;
        this.availableSeats = capacity;
        this.status = EventStatus.DRAFT;
        this.createdAt = Instant.now();
    }

    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new InvalidEventStateException(id, status, EventStatus.PUBLISHED);
        }
        status = EventStatus.PUBLISHED;
    }

    public void close() {
        if (status != EventStatus.PUBLISHED) {
            throw new InvalidEventStateException(id, status, EventStatus.CLOSED);
        }
        status = EventStatus.CLOSED;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getVenue() { return venue; }
    public String getCity() { return city; }
    public LocalDateTime getStartDateTime() { return startDateTime; }
    public BigDecimal getPrice() { return price; }
    public int getCapacity() { return capacity; }
    public int getAvailableSeats() { return availableSeats; }
    public EventStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}
