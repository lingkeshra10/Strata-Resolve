package com.strataresolve.audit.service;

import com.strataresolve.shared.event.AssignmentCreatedEvent;
import com.strataresolve.shared.event.PriorityChangedEvent;
import com.strataresolve.shared.event.PropertyConfigChangedEvent;
import com.strataresolve.shared.event.StatusChangedEvent;
import com.strataresolve.shared.event.TicketCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventListener")
class AuditEventListenerTest {

    @Mock
    private AuditService auditService;

    private AuditEventListener auditEventListener;

    private UUID propertyId;
    private UUID actingUserId;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        auditEventListener = new AuditEventListener(auditService);
        propertyId = UUID.randomUUID();
        actingUserId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("onTicketCreated")
    class OnTicketCreatedTests {

        @Test
        @DisplayName("should create audit event with TICKET_CREATED type")
        void shouldCreateAuditEventForTicketCreation() {
            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId, propertyId, ticketId,
                    UUID.randomUUID(), "SR-2025-000001", "PLUMBING", "HIGH");

            auditEventListener.onTicketCreated(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("TICKET_CREATED"),
                    eq(actingUserId),
                    eq("Ticket"),
                    eq(ticketId),
                    isNull(),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should include reference number, category, and priority in new value")
        void shouldIncludeTicketDetailsInNewValue() {
            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId, propertyId, ticketId,
                    UUID.randomUUID(), "SR-2025-000042", "ELECTRICAL", "URGENT");

            auditEventListener.onTicketCreated(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("TICKET_CREATED"),
                    eq(actingUserId),
                    eq("Ticket"),
                    eq(ticketId),
                    isNull(),
                    org.mockito.ArgumentMatchers.contains("SR-2025-000042")
            );
        }

        @Test
        @DisplayName("should have null previous value for creation events")
        void shouldHaveNullPreviousValue() {
            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId, propertyId, ticketId,
                    UUID.randomUUID(), "SR-2025-000001", "PLUMBING", "LOW");

            auditEventListener.onTicketCreated(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    isNull(),
                    any(String.class)
            );
        }
    }

    @Nested
    @DisplayName("onStatusChanged")
    class OnStatusChangedTests {

        @Test
        @DisplayName("should create audit event with STATUS_CHANGED type")
        void shouldCreateAuditEventForStatusChange() {
            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId, propertyId, ticketId,
                    "SUBMITTED", "ACKNOWLEDGED", null);

            auditEventListener.onStatusChanged(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("STATUS_CHANGED"),
                    eq(actingUserId),
                    eq("Ticket"),
                    eq(ticketId),
                    any(String.class),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should include previous status in previous value")
        void shouldIncludePreviousStatus() {
            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId, propertyId, ticketId,
                    "IN_PROGRESS", "READY_FOR_VERIFICATION", null);

            auditEventListener.onStatusChanged(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.contains("IN_PROGRESS"),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should include new status in new value")
        void shouldIncludeNewStatus() {
            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId, propertyId, ticketId,
                    "SUBMITTED", "ACKNOWLEDGED", null);

            auditEventListener.onStatusChanged(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    any(String.class),
                    org.mockito.ArgumentMatchers.contains("ACKNOWLEDGED")
            );
        }

        @Test
        @DisplayName("should include reason in new value when present")
        void shouldIncludeReasonWhenPresent() {
            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId, propertyId, ticketId,
                    "RESOLVED", "REOPENED", "Issue not fixed");

            auditEventListener.onStatusChanged(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    any(String.class),
                    org.mockito.ArgumentMatchers.contains("Issue not fixed")
            );
        }

        @Test
        @DisplayName("should not include reason key when reason is null")
        void shouldNotIncludeReasonWhenNull() {
            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId, propertyId, ticketId,
                    "SUBMITTED", "ACKNOWLEDGED", null);

            auditEventListener.onStatusChanged(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    any(String.class),
                    org.mockito.ArgumentMatchers.argThat(newValue ->
                            !newValue.contains("reason"))
            );
        }
    }

    @Nested
    @DisplayName("onAssignmentCreated")
    class OnAssignmentCreatedTests {

        @Test
        @DisplayName("should create audit event with ASSIGNMENT_CREATED type")
        void shouldCreateAuditEventForAssignment() {
            UUID assigneeId = UUID.randomUUID();
            AssignmentCreatedEvent event = new AssignmentCreatedEvent(
                    actingUserId, propertyId, ticketId, assigneeId, "TECHNICIAN");

            auditEventListener.onAssignmentCreated(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("ASSIGNMENT_CREATED"),
                    eq(actingUserId),
                    eq("Ticket"),
                    eq(ticketId),
                    isNull(),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should include assignee ID and type in new value")
        void shouldIncludeAssigneeDetails() {
            UUID assigneeId = UUID.randomUUID();
            AssignmentCreatedEvent event = new AssignmentCreatedEvent(
                    actingUserId, propertyId, ticketId, assigneeId, "VENDOR");

            auditEventListener.onAssignmentCreated(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    isNull(),
                    org.mockito.ArgumentMatchers.argThat(newValue ->
                            newValue.contains(assigneeId.toString()) &&
                                    newValue.contains("VENDOR"))
            );
        }

        @Test
        @DisplayName("should have null previous value for assignment creation")
        void shouldHaveNullPreviousValue() {
            AssignmentCreatedEvent event = new AssignmentCreatedEvent(
                    actingUserId, propertyId, ticketId, UUID.randomUUID(), "TECHNICIAN");

            auditEventListener.onAssignmentCreated(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    isNull(),
                    any(String.class)
            );
        }
    }

    @Nested
    @DisplayName("onPriorityChanged")
    class OnPriorityChangedTests {

        @Test
        @DisplayName("should create audit event with PRIORITY_CHANGED type")
        void shouldCreateAuditEventForPriorityChange() {
            PriorityChangedEvent event = new PriorityChangedEvent(
                    actingUserId, propertyId, ticketId, "LOW", "HIGH");

            auditEventListener.onPriorityChanged(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("PRIORITY_CHANGED"),
                    eq(actingUserId),
                    eq("Ticket"),
                    eq(ticketId),
                    any(String.class),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should include previous priority in previous value")
        void shouldIncludePreviousPriority() {
            PriorityChangedEvent event = new PriorityChangedEvent(
                    actingUserId, propertyId, ticketId, "NORMAL", "EMERGENCY");

            auditEventListener.onPriorityChanged(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.contains("NORMAL"),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should include new priority in new value")
        void shouldIncludeNewPriority() {
            PriorityChangedEvent event = new PriorityChangedEvent(
                    actingUserId, propertyId, ticketId, "LOW", "URGENT");

            auditEventListener.onPriorityChanged(event);

            verify(auditService).createAuditEvent(
                    any(), any(), any(), any(), any(),
                    any(String.class),
                    org.mockito.ArgumentMatchers.contains("URGENT")
            );
        }
    }

    @Nested
    @DisplayName("onPropertyConfigChanged")
    class OnPropertyConfigChangedTests {

        @Test
        @DisplayName("should create audit event for property config creation")
        void shouldCreateAuditEventForPropertyConfigCreation() {
            UUID entityId = UUID.randomUUID();
            PropertyConfigChangedEvent event = new PropertyConfigChangedEvent(
                    actingUserId, propertyId,
                    "Property", entityId,
                    "CREATED", null, "{\"name\":\"Maple Towers\"}");

            auditEventListener.onPropertyConfigChanged(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("PROPERTY_CONFIG_CHANGED_CREATED"),
                    eq(actingUserId),
                    eq("Property"),
                    eq(entityId),
                    isNull(),
                    eq("{\"name\":\"Maple Towers\"}")
            );
        }

        @Test
        @DisplayName("should create audit event for property config update")
        void shouldCreateAuditEventForPropertyConfigUpdate() {
            UUID entityId = UUID.randomUUID();
            PropertyConfigChangedEvent event = new PropertyConfigChangedEvent(
                    actingUserId, propertyId,
                    "Block", entityId,
                    "UPDATED", "{\"name\":\"Tower A\"}", "{\"name\":\"Tower Alpha\"}");

            auditEventListener.onPropertyConfigChanged(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("PROPERTY_CONFIG_CHANGED_UPDATED"),
                    eq(actingUserId),
                    eq("Block"),
                    eq(entityId),
                    eq("{\"name\":\"Tower A\"}"),
                    eq("{\"name\":\"Tower Alpha\"}")
            );
        }

        @Test
        @DisplayName("should create audit event for property config deletion")
        void shouldCreateAuditEventForPropertyConfigDeletion() {
            UUID entityId = UUID.randomUUID();
            PropertyConfigChangedEvent event = new PropertyConfigChangedEvent(
                    actingUserId, propertyId,
                    "Vendor", entityId,
                    "DELETED", "{\"name\":\"ABC Plumbing\"}", null);

            auditEventListener.onPropertyConfigChanged(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("PROPERTY_CONFIG_CHANGED_DELETED"),
                    eq(actingUserId),
                    eq("Vendor"),
                    eq(entityId),
                    eq("{\"name\":\"ABC Plumbing\"}"),
                    isNull()
            );
        }

        @Test
        @DisplayName("should handle SLA policy changes")
        void shouldHandleSlaPolicyChanges() {
            UUID policyId = UUID.randomUUID();
            PropertyConfigChangedEvent event = new PropertyConfigChangedEvent(
                    actingUserId, propertyId,
                    "SlaPolicy", policyId,
                    "UPDATED",
                    "{\"acknowledgementHours\":4,\"resolutionHours\":24}",
                    "{\"acknowledgementHours\":2,\"resolutionHours\":12}");

            auditEventListener.onPropertyConfigChanged(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("PROPERTY_CONFIG_CHANGED_UPDATED"),
                    eq(actingUserId),
                    eq("SlaPolicy"),
                    eq(policyId),
                    eq("{\"acknowledgementHours\":4,\"resolutionHours\":24}"),
                    eq("{\"acknowledgementHours\":2,\"resolutionHours\":12}")
            );
        }

        @Test
        @DisplayName("should handle membership changes")
        void shouldHandleMembershipChanges() {
            UUID membershipId = UUID.randomUUID();
            PropertyConfigChangedEvent event = new PropertyConfigChangedEvent(
                    actingUserId, propertyId,
                    "Membership", membershipId,
                    "CREATED", null, "{\"role\":\"RESIDENT_OWNER\",\"userId\":\"xyz\"}");

            auditEventListener.onPropertyConfigChanged(event);

            verify(auditService).createAuditEvent(
                    eq(propertyId),
                    eq("PROPERTY_CONFIG_CHANGED_CREATED"),
                    eq(actingUserId),
                    eq("Membership"),
                    eq(membershipId),
                    isNull(),
                    eq("{\"role\":\"RESIDENT_OWNER\",\"userId\":\"xyz\"}")
            );
        }
    }
}
