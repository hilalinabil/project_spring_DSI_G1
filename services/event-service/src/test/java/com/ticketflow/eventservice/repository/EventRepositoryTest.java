package com.ticketflow.eventservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.eventservice.entity.Event;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
class EventRepositoryTest {

    @Autowired
    EventRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    private Event saveEvent(int capacity, boolean publish) {
        Event event = new Event("Concert", "desc", "Arena", "Rabat",
                LocalDateTime.now().plusDays(10), new BigDecimal("150.00"), capacity);
        if (publish) {
            event.publish();
        }
        return repository.saveAndFlush(event);
    }

    private int seats(Long id) {
        return repository.findById(id).orElseThrow().getAvailableSeats();
    }

    @Test
    void reserveDecrementsSeats() {
        Event event = saveEvent(10, true);

        assertThat(repository.reserveSeats(event.getId(), 4)).isEqualTo(1);
        assertThat(seats(event.getId())).isEqualTo(6);
    }

    @Test
    void availableSeatsCanNeverGoNegative() {
        Event event = saveEvent(5, true);

        assertThat(repository.reserveSeats(event.getId(), 6)).isZero();   // more than capacity
        assertThat(seats(event.getId())).isEqualTo(5);                     // unchanged

        assertThat(repository.reserveSeats(event.getId(), 5)).isEqualTo(1); // exactly the rest
        assertThat(seats(event.getId())).isZero();

        assertThat(repository.reserveSeats(event.getId(), 1)).isZero();    // sold out
        assertThat(seats(event.getId())).isZero();
    }

    @Test
    void reserveIsRefusedOnADraftEvent() {
        Event event = saveEvent(10, false);

        assertThat(repository.reserveSeats(event.getId(), 1)).isZero();
        assertThat(seats(event.getId())).isEqualTo(10);
    }

    @Test
    void reserveOnUnknownEventUpdatesNothing() {
        assertThat(repository.reserveSeats(999_999L, 1)).isZero();
    }

    @Test
    void releaseNeverGoesAboveCapacity() {
        Event event = saveEvent(10, true);
        repository.reserveSeats(event.getId(), 3);

        assertThat(repository.releaseSeats(event.getId(), 2)).isEqualTo(1);
        assertThat(seats(event.getId())).isEqualTo(9);

        assertThat(repository.releaseSeats(event.getId(), 50)).isEqualTo(1);
        assertThat(seats(event.getId())).isEqualTo(10);
    }

    /**
     * 30 concurrent single-seat reservations on a 10-seat event, each in its own
     * committed transaction: exactly 10 succeed and the count stops at 0.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReservationsNeverOversell() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long id = tx.execute(status -> saveEvent(10, true).getId());

        int attempts = 30;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                Callable<Integer> reserveOne = () -> {
                    start.await();
                    return tx.execute(status -> repository.reserveSeats(id, 1));
                };
                results.add(pool.submit(reserveOne));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Integer> result : results) {
                succeeded += result.get();
            }

            assertThat(succeeded).isEqualTo(10);
            Integer remaining = tx.execute(status -> seats(id));
            assertThat(remaining).isZero();
        } finally {
            pool.shutdownNow();
            tx.executeWithoutResult(status -> repository.deleteById(id));
        }
    }
}
