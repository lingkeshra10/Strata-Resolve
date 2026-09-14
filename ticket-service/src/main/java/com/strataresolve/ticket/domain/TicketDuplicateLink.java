package com.strataresolve.ticket.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a link between a primary ticket and a duplicate ticket.
 * Created when a Property Manager manually confirms that a ticket is a duplicate of another.
 */
@Entity
@Table(name = "ticket_duplicate_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDuplicateLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "primary_ticket_id", nullable = false)
    private UUID primaryTicketId;

    @Column(name = "duplicate_ticket_id", nullable = false)
    private UUID duplicateTicketId;

    @Column(name = "linked_by", nullable = false)
    private UUID linkedBy;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @PrePersist
    protected void onCreate() {
        if (this.linkedAt == null) {
            this.linkedAt = Instant.now();
        }
    }
}
