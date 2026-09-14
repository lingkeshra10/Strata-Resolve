package com.strataresolve.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a row in the reference_number_sequence table.
 * Each row tracks the last-used sequential number for a given calendar year.
 * The year serves as the primary key — one row per year.
 */
@Entity
@Table(name = "reference_number_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceNumberSequence {

    @Id
    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "last_number", nullable = false)
    private Integer lastNumber;

    /**
     * Atomically increments the sequence and returns the new value.
     */
    public int incrementAndGet() {
        this.lastNumber++;
        return this.lastNumber;
    }
}
