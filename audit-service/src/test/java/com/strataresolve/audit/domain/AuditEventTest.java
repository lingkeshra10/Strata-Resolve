package com.strataresolve.audit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditEvent Entity")
class AuditEventTest {

    @Test
    @DisplayName("should create audit event with all fields populated")
    void shouldCreateAuditEventWithAllFields() {
        UUID propertyId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        UUID targetEntityId = UUID.randomUUID();
        String eventType = "TICKET_CREATED";
        String targetEntityType = "Ticket";
        String previousValue = "{\"status\":\"SUBMITTED\"}";
        String newValue = "{\"status\":\"ACKNOWLEDGED\"}";

        AuditEvent auditEvent = new AuditEvent(
                propertyId, eventType, actingUserId,
                targetEntityType, targetEntityId,
                previousValue, newValue
        );

        assertThat(auditEvent.getPropertyId()).isEqualTo(propertyId);
        assertThat(auditEvent.getEventType()).isEqualTo(eventType);
        assertThat(auditEvent.getActingUserId()).isEqualTo(actingUserId);
        assertThat(auditEvent.getTargetEntityType()).isEqualTo(targetEntityType);
        assertThat(auditEvent.getTargetEntityId()).isEqualTo(targetEntityId);
        assertThat(auditEvent.getPreviousValue()).isEqualTo(previousValue);
        assertThat(auditEvent.getNewValue()).isEqualTo(newValue);
    }

    @Test
    @DisplayName("should allow null previous value for creation events")
    void shouldAllowNullPreviousValue() {
        AuditEvent auditEvent = new AuditEvent(
                UUID.randomUUID(), "TICKET_CREATED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(),
                null, "{\"referenceNumber\":\"SR-2025-000001\"}"
        );

        assertThat(auditEvent.getPreviousValue()).isNull();
        assertThat(auditEvent.getNewValue()).isNotNull();
    }

    @Test
    @DisplayName("should allow null new value for deletion events")
    void shouldAllowNullNewValue() {
        AuditEvent auditEvent = new AuditEvent(
                UUID.randomUUID(), "PROPERTY_CONFIG_CHANGED_DELETED", UUID.randomUUID(),
                "Vendor", UUID.randomUUID(),
                "{\"name\":\"ABC Plumbing\"}", null
        );

        assertThat(auditEvent.getPreviousValue()).isNotNull();
        assertThat(auditEvent.getNewValue()).isNull();
    }

    @Test
    @DisplayName("should set createdAt on prePersist callback")
    void shouldSetCreatedAtOnPrePersist() {
        AuditEvent auditEvent = new AuditEvent(
                UUID.randomUUID(), "STATUS_CHANGED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(),
                "{\"status\":\"SUBMITTED\"}", "{\"status\":\"ACKNOWLEDGED\"}"
        );

        // createdAt is null before persist
        assertThat(auditEvent.getCreatedAt()).isNull();

        // Simulate JPA lifecycle callback
        auditEvent.onCreate();

        assertThat(auditEvent.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should have null id before persistence")
    void shouldHaveNullIdBeforePersistence() {
        AuditEvent auditEvent = new AuditEvent(
                UUID.randomUUID(), "TICKET_CREATED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(),
                null, "{}"
        );

        assertThat(auditEvent.getId()).isNull();
    }
}
