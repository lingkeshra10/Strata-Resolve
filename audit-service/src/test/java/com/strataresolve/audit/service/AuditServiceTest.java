package com.strataresolve.audit.service;

import com.strataresolve.audit.domain.AuditEvent;
import com.strataresolve.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService")
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditEventRepository);
    }

    @Test
    @DisplayName("should create audit event with all provided fields")
    void shouldCreateAuditEventWithAllFields() {
        UUID propertyId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        UUID targetEntityId = UUID.randomUUID();
        String eventType = "TICKET_CREATED";
        String targetEntityType = "Ticket";
        String previousValue = null;
        String newValue = "{\"referenceNumber\":\"SR-2025-000001\"}";

        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuditEvent result = auditService.createAuditEvent(
                propertyId, eventType, actingUserId,
                targetEntityType, targetEntityId,
                previousValue, newValue
        );

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getPropertyId()).isEqualTo(propertyId);
        assertThat(saved.getEventType()).isEqualTo(eventType);
        assertThat(saved.getActingUserId()).isEqualTo(actingUserId);
        assertThat(saved.getTargetEntityType()).isEqualTo(targetEntityType);
        assertThat(saved.getTargetEntityId()).isEqualTo(targetEntityId);
        assertThat(saved.getPreviousValue()).isNull();
        assertThat(saved.getNewValue()).isEqualTo(newValue);
    }

    @Test
    @DisplayName("should persist audit event with both previous and new values")
    void shouldPersistWithBothValues() {
        UUID propertyId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        UUID targetEntityId = UUID.randomUUID();
        String previousValue = "{\"status\":\"SUBMITTED\"}";
        String newValue = "{\"status\":\"ACKNOWLEDGED\"}";

        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        auditService.createAuditEvent(
                propertyId, "STATUS_CHANGED", actingUserId,
                "Ticket", targetEntityId,
                previousValue, newValue
        );

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getPreviousValue()).isEqualTo(previousValue);
        assertThat(saved.getNewValue()).isEqualTo(newValue);
    }

    @Test
    @DisplayName("should return the saved audit event")
    void shouldReturnSavedAuditEvent() {
        UUID propertyId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        UUID targetEntityId = UUID.randomUUID();

        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuditEvent result = auditService.createAuditEvent(
                propertyId, "ASSIGNMENT_CREATED", actingUserId,
                "Ticket", targetEntityId,
                null, "{\"assigneeId\":\"abc\"}"
        );

        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo("ASSIGNMENT_CREATED");
    }
}
