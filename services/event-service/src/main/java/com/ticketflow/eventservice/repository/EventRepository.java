package com.ticketflow.eventservice.repository;

import com.ticketflow.eventservice.entity.Event;
import com.ticketflow.eventservice.entity.EventStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    Optional<Event> findByIdAndStatus(Long id, EventStatus status);

    /**
     * Reserves seats atomically: one conditional UPDATE, so two concurrent
     * reservations can never push availableSeats below zero.
     *
     * @return 1 if reserved, 0 if the event is unknown, not PUBLISHED, or has
     *         fewer than {@code quantity} seats left
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Event e
               set e.availableSeats = e.availableSeats - :quantity,
                   e.version = e.version + 1
             where e.id = :id
               and e.status = com.ticketflow.eventservice.entity.EventStatus.PUBLISHED
               and e.availableSeats >= :quantity
            """)
    int reserveSeats(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * Gives seats back (compensation), capped at the capacity.
     *
     * @return 1 if the event exists, 0 otherwise
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Event e
               set e.availableSeats = case
                       when e.availableSeats + :quantity > e.capacity then e.capacity
                       else e.availableSeats + :quantity end,
                   e.version = e.version + 1
             where e.id = :id
            """)
    int releaseSeats(@Param("id") Long id, @Param("quantity") int quantity);
}
