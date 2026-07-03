package com.strataresolve.ticket.service;

import com.strataresolve.ticket.domain.ReferenceNumberSequence;
import com.strataresolve.ticket.repository.ReferenceNumberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Service responsible for generating sequential, gap-free reference numbers
 * in the format SR-YYYY-NNNNNN.
 *
 * Uses pessimistic locking (SELECT ... FOR UPDATE) to ensure atomic increment
 * of the sequence within a calendar year. If no sequence row exists for the
 * current year, one is created starting at 0.
 */
@Service
public class ReferenceNumberGenerator {

    private final ReferenceNumberRepository referenceNumberRepository;

    public ReferenceNumberGenerator(ReferenceNumberRepository referenceNumberRepository) {
        this.referenceNumberRepository = referenceNumberRepository;
    }

    /**
     * Generates the next reference number for the current calendar year.
     * The format is SR-YYYY-NNNNNN where NNNNNN is a zero-padded sequential number.
     *
     * This method MUST be called within an active transaction to ensure the
     * pessimistic lock is held for the duration of the increment.
     *
     * @return the next reference number string (e.g., "SR-2025-000001")
     */
    @Transactional
    public String generateReferenceNumber() {
        int year = Year.now().getValue();
        int nextNumber = incrementAndGet(year);
        return String.format("SR-%d-%06d", year, nextNumber);
    }

    /**
     * Atomically increments the sequence for the given year and returns the new value.
     * If no row exists for the year, creates one starting at 1.
     */
    private int incrementAndGet(int year) {
        ReferenceNumberSequence sequence = referenceNumberRepository.findByYearForUpdate(year)
                .orElseGet(() -> {
                    ReferenceNumberSequence newSequence = new ReferenceNumberSequence(year, 0);
                    return referenceNumberRepository.save(newSequence);
                });

        return sequence.incrementAndGet();
    }
}
