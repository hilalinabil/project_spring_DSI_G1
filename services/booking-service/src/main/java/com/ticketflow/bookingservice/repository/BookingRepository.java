package com.ticketflow.bookingservice.repository;

import com.ticketflow.bookingservice.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByCustomerId(Long customerId, Pageable pageable);
}
