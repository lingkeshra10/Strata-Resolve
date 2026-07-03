package com.strataresolve.vendor.domain;

import com.strataresolve.shared.tenant.TenantAwareEntity;
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
 * JPA entity representing a work order assigned to a vendor for a specific ticket.
 * Tracks the lifecycle from creation through acceptance, in-progress, to completion or cancellation.
 *
 * <p>A work order links a ticket to a vendor, making it visible to all active
 * Vendor_Technicians of the assigned vendor.
 */
@Entity
@Table(name = "work_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrder extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkOrderStatus status = WorkOrderStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    /**
     * Transitions the work order to ACCEPTED state.
     *
     * @throws IllegalStateException if the current status does not allow transition to ACCEPTED
     */
    public void accept() {
        validateTransition(WorkOrderStatus.ACCEPTED);
        this.status = WorkOrderStatus.ACCEPTED;
    }

    /**
     * Transitions the work order to IN_PROGRESS state.
     *
     * @throws IllegalStateException if the current status does not allow transition to IN_PROGRESS
     */
    public void startWork() {
        validateTransition(WorkOrderStatus.IN_PROGRESS);
        this.status = WorkOrderStatus.IN_PROGRESS;
    }

    /**
     * Transitions the work order to COMPLETED state and records the completion timestamp.
     *
     * @throws IllegalStateException if the current status does not allow transition to COMPLETED
     */
    public void complete() {
        validateTransition(WorkOrderStatus.COMPLETED);
        this.status = WorkOrderStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Transitions the work order to CANCELLED state.
     *
     * @throws IllegalStateException if the current status does not allow transition to CANCELLED
     */
    public void cancel() {
        validateTransition(WorkOrderStatus.CANCELLED);
        this.status = WorkOrderStatus.CANCELLED;
    }

    private void validateTransition(WorkOrderStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("Cannot transition work order from %s to %s", this.status, target));
        }
    }
}
