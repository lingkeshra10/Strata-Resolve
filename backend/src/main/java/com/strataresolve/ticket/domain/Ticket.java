package com.strataresolve.ticket.domain;

import com.strataresolve.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a maintenance ticket submitted by a resident.
 * Extends {@link TenantAwareEntity} to participate in multi-tenancy data isolation.
 */
@Entity
@Table(name = "ticket")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "reference_number", nullable = false, unique = true, length = 20)
    private String referenceNumber;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TicketStatus status = TicketStatus.SUBMITTED;

    @Column(length = 500)
    private String location;

    @Column(name = "acknowledgement_due_at")
    private Instant acknowledgementDueAt;

    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", nullable = false, length = 30)
    @Builder.Default
    private SlaStatus slaStatus = SlaStatus.ON_TRACK;

    @Column(name = "duplicate_flag", nullable = false)
    @Builder.Default
    private boolean duplicateFlag = false;

    @Column(name = "linked_to_ticket_id")
    private UUID linkedToTicketId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
