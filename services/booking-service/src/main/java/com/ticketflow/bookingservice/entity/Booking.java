package com.ticketflow.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A booking only stores the ids of the customer and the event: they live in
 * other services (and other databases). The price is a snapshot taken from
 * event-service when the booking is made.
 */
@Entity
@Table(name = "bookings", indexes = @Index(name = "idx_bookings_customer_id", columnList = "customerId"))
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, updatable = false)
    private Long eventId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Booking() {
        // required by JPA
    }

    /** A new booking waits for payment; totalPrice = unitPrice x quantity. */
    public Booking(Long customerId, Long eventId, int quantity, BigDecimal unitPrice) {
        this.customerId = customerId;
        this.eventId = eventId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.status = BookingStatus.PENDING_PAYMENT;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getEventId() { return eventId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public BookingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
