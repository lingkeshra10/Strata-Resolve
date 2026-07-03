package com.strataresolve.audit.controller;

import com.strataresolve.audit.domain.AuditEvent;
import com.strataresolve.audit.dto.AuditEventResponse;
import com.strataresolve.audit.service.AuditQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditController")
class AuditControllerTest {

    @Mock
    private AuditQueryService auditQueryService;

    private AuditController auditController;

    private final UUID propertyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditController = new AuditController(auditQueryService);
    }

    @Test
    @DisplayName("should return all audit events when no filters provided")
    void shouldReturnAllEventsWhenNoFilters() {
        AuditEvent event = new AuditEvent(propertyId, "TICKET_CREATED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(), null, "{\"title\":\"Test\"}");

        when(auditQueryService.findByPropertyId(propertyId)).thenReturn(List.of(event));

        ResponseEntity<List<AuditEventResponse>> response = auditController.queryAuditTrail(
                propertyId, null, null, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).eventType()).isEqualTo("TICKET_CREATED");
        assertThat(response.getBody().get(0).propertyId()).isEqualTo(propertyId);
        verify(auditQueryService).findByPropertyId(propertyId);
    }

    @Test
    @DisplayName("should return events filtered by date range")
    void shouldReturnEventsFilteredByDateRange() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        AuditEvent event = new AuditEvent(propertyId, "STATUS_CHANGED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(), "{\"status\":\"SUBMITTED\"}", "{\"status\":\"ACKNOWLEDGED\"}");

        when(auditQueryService.findByDateRange(propertyId, from, to)).thenReturn(List.of(event));

        ResponseEntity<List<AuditEventResponse>> response = auditController.queryAuditTrail(
                propertyId, from, to, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).eventType()).isEqualTo("STATUS_CHANGED");
        verify(auditQueryService).findByDateRange(propertyId, from, to);
    }

    @Test
    @DisplayName("should return events filtered by event type")
    void shouldReturnEventsFilteredByEventType() {
        AuditEvent event = new AuditEvent(propertyId, "PRIORITY_CHANGED", UUID.randomUUID(),
                "Ticket", UUID.randomUUID(), "{\"priority\":\"LOW\"}", "{\"priority\":\"HIGH\"}");

        when(auditQueryService.findByEventType(propertyId, "PRIORITY_CHANGED")).thenReturn(List.of(event));

        ResponseEntity<List<AuditEventResponse>> response = auditController.queryAuditTrail(
                propertyId, null, null, "PRIORITY_CHANGED", null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).eventType()).isEqualTo("PRIORITY_CHANGED");
        verify(auditQueryService).findByEventType(propertyId, "PRIORITY_CHANGED");
    }

    @Test
    @DisplayName("should return events filtered by acting user")
    void shouldReturnEventsFilteredByActingUser() {
        UUID actingUserId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(propertyId, "TICKET_CREATED", actingUserId,
                "Ticket", UUID.randomUUID(), null, "{\"title\":\"Test\"}");

        when(auditQueryService.findByActingUser(propertyId, actingUserId)).thenReturn(List.of(event));

        ResponseEntity<List<AuditEventResponse>> response = auditController.queryAuditTrail(
                propertyId, null, null, null, actingUserId, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).actingUserId()).isEqualTo(actingUserId);
        verify(auditQueryService).findByActingUser(propertyId, actingUserId);
    }

    @Test
    @DisplayName("should return events filtered by target entity")
    void shouldReturnEventsFilteredByTargetEntity() {
        UUID targetEntityId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(propertyId, "STATUS_CHANGED", UUID.randomUUID(),
                "Ticket", targetEntityId, "{\"status\":\"ASSIGNED\"}", "{\"status\":\"IN_PROGRESS\"}");

        when(auditQueryService.findByTargetEntity(propertyId, "Ticket", targetEntityId)).thenReturn(List.of(event));

        ResponseEntity<List<AuditEventResponse>> response = auditController.queryAuditTrail(
                propertyId, null, null, null, null, "Ticket", targetEntityId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).targetEntityId()).isEqualTo(targetEntityId);
        verify(auditQueryService).findByTargetEntity(propertyId, "Ticket", targetEntityId);
    }

    @Test
    @DisplayName("should return empty list when no events found")
    void shouldReturnEmptyListWhenNoEvents() {
        when(auditQueryService.findByPropertyId(propertyId)).thenReturn(List.of());

        ResponseEntity<List<AuditEventResponse>> response = auditController.queryAuditTrail(
                propertyId, null, null, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("date range filter should take precedence over event type")
    void dateRangeShouldTakePrecedenceOverEventType() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        when(auditQueryService.findByDateRange(propertyId, from, to)).thenReturn(List.of());

        ResponseEntity<List<AuditEventResponse>> response = auditController.queryAuditTrail(
                propertyId, from, to, "TICKET_CREATED", null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(auditQueryService).findByDateRange(propertyId, from, to);
    }

    @Test
    @DisplayName("event type filter should take precedence over acting user")
    void eventTypeShouldTakePrecedenceOverActingUser() {
        UUID actingUserId = UUID.randomUUID();

        when(auditQueryService.findByEventType(propertyId, "STATUS_CHANGED")).thenReturn(List.of());

        ResponseEntity<List<AuditEventResponse>> response = auditController.queryAuditTrail(
                propertyId, null, null, "STATUS_CHANGED", actingUserId, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(auditQueryService).findByEventType(propertyId, "STATUS_CHANGED");
    }
}
