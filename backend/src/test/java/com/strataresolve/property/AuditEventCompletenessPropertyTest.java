package com.strataresolve.property;

import com.strataresolve.audit.domain.AuditEvent;
import com.strataresolve.audit.repository.AuditEventRepository;
import com.strataresolve.audit.service.AuditEventListener;
import com.strataresolve.audit.service.AuditService;
import com.strataresolve.shared.event.AssignmentCreatedEvent;
import com.strataresolve.shared.event.PriorityChangedEvent;
import com.strataresolve.shared.event.PropertyConfigChangedEvent;
import com.strataresolve.shared.event.StatusChangedEvent;
import com.strataresolve.shared.event.TicketCreatedEvent;
import net.jqwik.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Audit Event Completeness.
 *
 * <p><b>Property 8: Audit Event Completeness</b></p>
 * <p><b>Validates: Requirements 17.1, 17.2</b></p>
 *
 * <p>For any significant action (ticket creation, status change, assignment, priority change,
 * category change, membership change, SLA policy change, property configuration change),
 * the system SHALL create an audit event containing event type, acting user, target entity,
 * timestamp, previous value, new value, and property context.</p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 8: Audit Event Completeness")
class AuditEventCompletenessPropertyTest {

    /**
     * Property: For any TicketCreatedEvent, the audit record SHALL contain non-null
     * event type, acting user, target entity type, target entity id, and property context.
     *
     * Validates: Requirements 17.1, 17.2
     */
    @Property(tries = 100)
    void ticketCreatedEventProducesCompleteAuditRecord(
            @ForAll("actingUserIds") UUID actingUserId,
            @ForAll("propertyIds") UUID propertyId,
            @ForAll("entityIds") UUID ticketId,
            @ForAll("entityIds") UUID unitId,
            @ForAll("referenceNumbers") String referenceNumber,
            @ForAll("categories") String category,
            @ForAll("priorities") String priority
    ) {
        // Arrange
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditService auditService = new AuditService(repository);
        AuditEventListener listener = new AuditEventListener(auditService);

        TicketCreatedEvent event = new TicketCreatedEvent(
                actingUserId, propertyId, ticketId, unitId, referenceNumber, category, priority);

        // Act
        listener.onTicketCreated(event);

        // Assert
        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent auditEvent = captor.getValue();

        assertAuditEventIsComplete(auditEvent, propertyId, actingUserId);
        assertThat(auditEvent.getEventType()).isEqualTo("TICKET_CREATED");
        assertThat(auditEvent.getTargetEntityType()).isEqualTo("Ticket");
        assertThat(auditEvent.getTargetEntityId()).isEqualTo(ticketId);
        // For creation events, newValue should be populated
        assertThat(auditEvent.getNewValue()).isNotNull().isNotEmpty();
    }

    /**
     * Property: For any StatusChangedEvent, the audit record SHALL contain non-null
     * event type, acting user, target entity, property context, and both previous and new values.
     *
     * Validates: Requirements 17.1, 17.2
     */
    @Property(tries = 100)
    void statusChangedEventProducesCompleteAuditRecord(
            @ForAll("actingUserIds") UUID actingUserId,
            @ForAll("propertyIds") UUID propertyId,
            @ForAll("entityIds") UUID ticketId,
            @ForAll("ticketStatuses") String previousStatus,
            @ForAll("ticketStatuses") String newStatus,
            @ForAll("optionalReasons") String reason
    ) {
        // Arrange
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditService auditService = new AuditService(repository);
        AuditEventListener listener = new AuditEventListener(auditService);

        StatusChangedEvent event = new StatusChangedEvent(
                actingUserId, propertyId, ticketId, previousStatus, newStatus, reason);

        // Act
        listener.onStatusChanged(event);

        // Assert
        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent auditEvent = captor.getValue();

        assertAuditEventIsComplete(auditEvent, propertyId, actingUserId);
        assertThat(auditEvent.getEventType()).isEqualTo("STATUS_CHANGED");
        assertThat(auditEvent.getTargetEntityType()).isEqualTo("Ticket");
        assertThat(auditEvent.getTargetEntityId()).isEqualTo(ticketId);
        assertThat(auditEvent.getPreviousValue()).isNotNull().isNotEmpty();
        assertThat(auditEvent.getNewValue()).isNotNull().isNotEmpty();
    }

    /**
     * Property: For any AssignmentCreatedEvent, the audit record SHALL contain non-null
     * event type, acting user, target entity, property context, and new value with assignment details.
     *
     * Validates: Requirements 17.1, 17.2
     */
    @Property(tries = 100)
    void assignmentCreatedEventProducesCompleteAuditRecord(
            @ForAll("actingUserIds") UUID actingUserId,
            @ForAll("propertyIds") UUID propertyId,
            @ForAll("entityIds") UUID ticketId,
            @ForAll("entityIds") UUID assigneeId,
            @ForAll("assignmentTypes") String assignmentType
    ) {
        // Arrange
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditService auditService = new AuditService(repository);
        AuditEventListener listener = new AuditEventListener(auditService);

        AssignmentCreatedEvent event = new AssignmentCreatedEvent(
                actingUserId, propertyId, ticketId, assigneeId, assignmentType);

        // Act
        listener.onAssignmentCreated(event);

        // Assert
        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent auditEvent = captor.getValue();

        assertAuditEventIsComplete(auditEvent, propertyId, actingUserId);
        assertThat(auditEvent.getEventType()).isEqualTo("ASSIGNMENT_CREATED");
        assertThat(auditEvent.getTargetEntityType()).isEqualTo("Ticket");
        assertThat(auditEvent.getTargetEntityId()).isEqualTo(ticketId);
        assertThat(auditEvent.getNewValue()).isNotNull().isNotEmpty();
    }

    /**
     * Property: For any PriorityChangedEvent, the audit record SHALL contain non-null
     * event type, acting user, target entity, property context, and both previous and new priority values.
     *
     * Validates: Requirements 17.1, 17.2
     */
    @Property(tries = 100)
    void priorityChangedEventProducesCompleteAuditRecord(
            @ForAll("actingUserIds") UUID actingUserId,
            @ForAll("propertyIds") UUID propertyId,
            @ForAll("entityIds") UUID ticketId,
            @ForAll("priorities") String previousPriority,
            @ForAll("priorities") String newPriority
    ) {
        // Arrange
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditService auditService = new AuditService(repository);
        AuditEventListener listener = new AuditEventListener(auditService);

        PriorityChangedEvent event = new PriorityChangedEvent(
                actingUserId, propertyId, ticketId, previousPriority, newPriority);

        // Act
        listener.onPriorityChanged(event);

        // Assert
        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent auditEvent = captor.getValue();

        assertAuditEventIsComplete(auditEvent, propertyId, actingUserId);
        assertThat(auditEvent.getEventType()).isEqualTo("PRIORITY_CHANGED");
        assertThat(auditEvent.getTargetEntityType()).isEqualTo("Ticket");
        assertThat(auditEvent.getTargetEntityId()).isEqualTo(ticketId);
        assertThat(auditEvent.getPreviousValue()).isNotNull().isNotEmpty();
        assertThat(auditEvent.getNewValue()).isNotNull().isNotEmpty();
    }

    /**
     * Property: For any PropertyConfigChangedEvent (covering membership change, SLA policy change,
     * property configuration change), the audit record SHALL contain non-null event type, acting user,
     * target entity, property context, and appropriate previous/new values.
     *
     * Validates: Requirements 17.1, 17.2
     */
    @Property(tries = 100)
    void propertyConfigChangedEventProducesCompleteAuditRecord(
            @ForAll("actingUserIds") UUID actingUserId,
            @ForAll("propertyIds") UUID propertyId,
            @ForAll("configEntityTypes") String entityType,
            @ForAll("entityIds") UUID entityId,
            @ForAll("configActions") String action,
            @ForAll("jsonValues") String previousValue,
            @ForAll("jsonValues") String newValue
    ) {
        // Arrange
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditService auditService = new AuditService(repository);
        AuditEventListener listener = new AuditEventListener(auditService);

        PropertyConfigChangedEvent event = new PropertyConfigChangedEvent(
                actingUserId, propertyId, entityType, entityId, action, previousValue, newValue);

        // Act
        listener.onPropertyConfigChanged(event);

        // Assert
        var captor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent auditEvent = captor.getValue();

        assertAuditEventIsComplete(auditEvent, propertyId, actingUserId);
        assertThat(auditEvent.getEventType())
                .isEqualTo("PROPERTY_CONFIG_CHANGED_" + action.toUpperCase());
        assertThat(auditEvent.getTargetEntityType()).isEqualTo(entityType);
        assertThat(auditEvent.getTargetEntityId()).isEqualTo(entityId);
    }

    // ======================== Shared Assertion ========================

    /**
     * Asserts that an audit event contains all required fields as per Requirements 17.2:
     * event type, acting user, target entity (type + id), property context (propertyId).
     */
    private void assertAuditEventIsComplete(AuditEvent auditEvent, UUID expectedPropertyId, UUID expectedActingUserId) {
        assertThat(auditEvent).as("Audit event must be created").isNotNull();
        assertThat(auditEvent.getEventType())
                .as("Audit event must have non-null event type")
                .isNotNull()
                .isNotEmpty();
        assertThat(auditEvent.getActingUserId())
                .as("Audit event must have non-null acting user")
                .isNotNull()
                .isEqualTo(expectedActingUserId);
        assertThat(auditEvent.getTargetEntityType())
                .as("Audit event must have non-null target entity type")
                .isNotNull()
                .isNotEmpty();
        assertThat(auditEvent.getTargetEntityId())
                .as("Audit event must have non-null target entity id")
                .isNotNull();
        assertThat(auditEvent.getPropertyId())
                .as("Audit event must have non-null property context")
                .isNotNull()
                .isEqualTo(expectedPropertyId);
    }

    // ======================== Generators ========================

    @Provide
    Arbitrary<UUID> actingUserIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<UUID> propertyIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<UUID> entityIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> referenceNumbers() {
        return Arbitraries.integers().between(1, 999999)
                .map(n -> String.format("SR-2025-%06d", n));
    }

    @Provide
    Arbitrary<String> categories() {
        return Arbitraries.of(
                "PLUMBING", "ELECTRICAL", "LIFT", "DRAINAGE", "SECURITY",
                "CLEANING", "STRUCTURAL", "ACCESS_CONTROL", "COMMON_FACILITIES",
                "PARKING", "LANDSCAPING", "OTHER"
        );
    }

    @Provide
    Arbitrary<String> priorities() {
        return Arbitraries.of("LOW", "NORMAL", "HIGH", "URGENT", "EMERGENCY");
    }

    @Provide
    Arbitrary<String> ticketStatuses() {
        return Arbitraries.of(
                "SUBMITTED", "ACKNOWLEDGED", "UNDER_REVIEW", "ASSIGNED",
                "IN_PROGRESS", "AWAITING_VENDOR", "AWAITING_RESIDENT",
                "READY_FOR_VERIFICATION", "RESOLVED", "CLOSED", "REOPENED",
                "REJECTED", "CANCELLED"
        );
    }

    @Provide
    Arbitrary<String> optionalReasons() {
        return Arbitraries.of("Issue persists", "Reassigned to another team", "Customer request", null);
    }

    @Provide
    Arbitrary<String> assignmentTypes() {
        return Arbitraries.of("TECHNICIAN", "VENDOR");
    }

    @Provide
    Arbitrary<String> configEntityTypes() {
        return Arbitraries.of("Property", "Block", "Unit", "Membership", "SlaPolicy", "Vendor");
    }

    @Provide
    Arbitrary<String> configActions() {
        return Arbitraries.of("CREATED", "UPDATED", "DEACTIVATED");
    }

    @Provide
    Arbitrary<String> jsonValues() {
        return Arbitraries.of(
                "{\"name\":\"Building A\"}",
                "{\"status\":\"ACTIVE\"}",
                "{\"role\":\"PROPERTY_MANAGER\"}",
                "{\"acknowledgementHours\":4,\"resolutionHours\":24}",
                "{\"contactEmail\":\"vendor@example.com\"}",
                null
        );
    }
}
