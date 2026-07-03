package com.strataresolve.audit.service;

import com.strataresolve.audit.domain.AuditEvent;
import com.strataresolve.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditQueryService")
class AuditQueryServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditQueryService auditQueryService;

    @BeforeEach
    void setUp() {
        auditQueryService = new AuditQueryService(auditEventRepository);
    }

    @Test
    @DisplayName("should return all audit events for a property")
    void shouldReturnAllEventsForProperty() {
        UUID propertyId = UUID.randomUUID();
        AuditEvent event1 = new AuditEvent(propertyId, "TICKET_CREATED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(), null, "{\"status\":\"SUBMITTED\"}");
        AuditEvent event2 = new AuditEvent(propertyId, "STATUS_CHANGED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(), "{\"status\":\"SUBMITTED\"}", "{\"status\":\"ACKNOWLEDGED\"}");

        when(auditEventRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId))
                .thenReturn(List.of(event1, event2));

        List<AuditEvent> result = auditQueryService.findByPropertyId(propertyId);

        assertThat(result).hasSize(2);
        verify(auditEventRepository).findByPropertyIdOrderByCreatedAtDesc(propertyId);
    }

    @Test
    @DisplayName("should return audit events filtered by date range")
    void shouldReturnEventsFilteredByDateRange() {
        UUID propertyId = UUID.randomUUID();
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        AuditEvent event = new AuditEvent(propertyId, "TICKET_CREATED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(), null, "{\"title\":\"Test\"}");

        when(auditEventRepository.findByPropertyIdAndDateRange(propertyId, from, to))
                .thenReturn(List.of(event));

        List<AuditEvent> result = auditQueryService.findByDateRange(propertyId, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("TICKET_CREATED");
        verify(auditEventRepository).findByPropertyIdAndDateRange(propertyId, from, to);
    }

    @Test
    @DisplayName("should return audit events filtered by event type")
    void shouldReturnEventsFilteredByEventType() {
        UUID propertyId = UUID.randomUUID();
        String eventType = "STATUS_CHANGED";
        AuditEvent event = new AuditEvent(propertyId, eventType, UUID.randomUUID(),
                "Ticket", UUID.randomUUID(), "{\"status\":\"SUBMITTED\"}", "{\"status\":\"ASSIGNED\"}");

        when(auditEventRepository.findByPropertyIdAndEventTypeOrderByCreatedAtDesc(propertyId, eventType))
                .thenReturn(List.of(event));

        List<AuditEvent> result = auditQueryService.findByEventType(propertyId, eventType);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo(eventType);
        verify(auditEventRepository).findByPropertyIdAndEventTypeOrderByCreatedAtDesc(propertyId, eventType);
    }

    @Test
    @DisplayName("should return audit events filtered by acting user")
    void shouldReturnEventsFilteredByActingUser() {
        UUID propertyId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(propertyId, "ASSIGNMENT_CREATED", actingUserId,
                "Ticket", UUID.randomUUID(), null, "{\"assigneeId\":\"abc\"}");

        when(auditEventRepository.findByPropertyIdAndActingUserIdOrderByCreatedAtDesc(propertyId, actingUserId))
                .thenReturn(List.of(event));

        List<AuditEvent> result = auditQueryService.findByActingUser(propertyId, actingUserId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActingUserId()).isEqualTo(actingUserId);
        verify(auditEventRepository).findByPropertyIdAndActingUserIdOrderByCreatedAtDesc(propertyId, actingUserId);
    }

    @Test
    @DisplayName("should return audit events filtered by target entity")
    void shouldReturnEventsFilteredByTargetEntity() {
        UUID propertyId = UUID.randomUUID();
        String targetEntityType = "Ticket";
        UUID targetEntityId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(propertyId, "TICKET_CREATED", UUID.randomUUID(),
                targetEntityType, targetEntityId, null, "{\"title\":\"Test\"}");

        when(auditEventRepository.findByPropertyIdAndTargetEntityTypeAndTargetEntityIdOrderByCreatedAtDesc(
                propertyId, targetEntityType, targetEntityId))
                .thenReturn(List.of(event));

        List<AuditEvent> result = auditQueryService.findByTargetEntity(propertyId, targetEntityType, targetEntityId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTargetEntityType()).isEqualTo(targetEntityType);
        assertThat(result.get(0).getTargetEntityId()).isEqualTo(targetEntityId);
        verify(auditEventRepository).findByPropertyIdAndTargetEntityTypeAndTargetEntityIdOrderByCreatedAtDesc(
                propertyId, targetEntityType, targetEntityId);
    }

    @Test
    @DisplayName("should return empty list when no events match")
    void shouldReturnEmptyListWhenNoEventsMatch() {
        UUID propertyId = UUID.randomUUID();

        when(auditEventRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId))
                .thenReturn(List.of());

        List<AuditEvent> result = auditQueryService.findByPropertyId(propertyId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty list when date range has no events")
    void shouldReturnEmptyListForEmptyDateRange() {
        UUID propertyId = UUID.randomUUID();
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = Instant.parse("2025-06-30T23:59:59Z");

        when(auditEventRepository.findByPropertyIdAndDateRange(propertyId, from, to))
                .thenReturn(List.of());

        List<AuditEvent> result = auditQueryService.findByDateRange(propertyId, from, to);

        assertThat(result).isEmpty();
    }
}
