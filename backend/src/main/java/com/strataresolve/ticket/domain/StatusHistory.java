package com.strataresolve.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity recording a single status transition for a ticket.
 * Each time a ticket changes status, a new StatusHistory entry is created.
 */
@Entity
@Table(name = "status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 30)
    private TicketStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private TicketStatus newStatus;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(length = 1000)
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) {
            this.changedAt = Instant.now();
        }
    }
}
