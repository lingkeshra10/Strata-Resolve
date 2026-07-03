package com.strataresolve.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable JPA entity representing an audit event record.
 *
 * <p>Audit events are append-only: once created, they cannot be modified or deleted
 * through the application. This entity intentionally does not expose setters for
 * critical fields after construction, enforcing immutability at the domain level.
 *
 * <p>Each audit event captures:
 * <ul>
 *   <li>Event type (e.g., TICKET_CREATED, STATUS_CHANGED)</li>
 *   <li>Acting user who performed the action</li>
 *   <li>Target entity type and ID</li>
 *   <li>Previous and new values as JSON (nullable)</li>
 *   <li>Property context for multi-tenancy</li>
 *   <li>Creation timestamp</li>
 * </ul>
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "acting_user_id", nullable = false)
    private UUID actingUserId;

    @Column(name = "target_entity_type", nullable = false, length = 100)
    private String targetEntityType;

    @Column(name = "target_entity_id", nullable = false)
    private UUID targetEntityId;

    @Column(name = "previous_value", columnDefinition = "TEXT")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * JPA requires a no-arg constructor.
     */
    protected AuditEvent() {
    }

    /**
     * Creates a new AuditEvent with all required fields.
     * This is the only way to create an audit event, ensuring immutability.
     */
    public AuditEvent(UUID propertyId, String eventType, UUID actingUserId,
                      String targetEntityType, UUID targetEntityId,
                      String previousValue, String newValue) {
        this.propertyId = propertyId;
        this.eventType = eventType;
        this.actingUserId = actingUserId;
        this.targetEntityType = targetEntityType;
        this.targetEntityId = targetEntityId;
        this.previousValue = previousValue;
        this.newValue = newValue;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Read-only accessors (no setters exposed) ---

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getActingUserId() {
        return actingUserId;
    }

    public String getTargetEntityType() {
        return targetEntityType;
    }

    public UUID getTargetEntityId() {
        return targetEntityId;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
