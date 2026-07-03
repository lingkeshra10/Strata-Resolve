package com.strataresolve.property;

import com.strataresolve.audit.domain.AuditEvent;
import com.strataresolve.audit.repository.AuditEventRepository;
import com.strataresolve.audit.service.AuditService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Audit Immutability.
 *
 * <p><b>Property 9: Audit Immutability</b></p>
 * <p>For any existing audit event record, any attempt to update or delete it through
 * the application SHALL be rejected. Audit records are append-only.</p>
 *
 * <p><b>Validates: Requirements 17.3</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 9: Audit Immutability")
class AuditImmutabilityPropertyTest {

    /**
     * Critical fields of AuditEvent that must not have setters.
     */
    private static final Set<String> CRITICAL_FIELDS = Set.of(
            "id", "propertyId", "eventType", "actingUserId",
            "targetEntityType", "targetEntityId",
            "previousValue", "newValue", "createdAt"
    );

    // =====================================================================
    // Property: AuditEvent entity SHALL NOT expose setter methods for any field
    // =====================================================================

    /**
     * For any critical field of the AuditEvent entity, there SHALL be no public
     * setter method. This enforces immutability at the domain level—once an
     * AuditEvent is created, its state cannot be modified through the entity API.
     *
     * <p><b>Validates: Requirements 17.3</b></p>
     */
    @Property(tries = 100)
    void auditEventEntityShallNotExposeSettersForCriticalFields(
            @ForAll("criticalFields") String fieldName
    ) {
        // Construct the expected setter method name
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        // Get all public methods of AuditEvent
        Method[] methods = AuditEvent.class.getMethods();

        // Assert: no public setter exists for this critical field
        boolean hasPublicSetter = Arrays.stream(methods)
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .anyMatch(m -> m.getName().equals(setterName));

        assertThat(hasPublicSetter)
                .as("AuditEvent must not expose public setter '%s' for immutable field '%s'",
                        setterName, fieldName)
                .isFalse();
    }

    // =====================================================================
    // Property: AuditService SHALL only expose creation (no update/delete API)
    // =====================================================================

    /**
     * For any method name on the AuditService class, none SHALL indicate an update
     * or delete operation. The service must be append-only—only creation is permitted.
     *
     * <p><b>Validates: Requirements 17.3</b></p>
     */
    @Property(tries = 100)
    void auditServiceShallNotExposeUpdateOrDeleteMethods(
            @ForAll("mutationKeywords") String keyword
    ) {
        // Get all public methods declared by AuditService (not inherited from Object)
        Method[] methods = AuditService.class.getDeclaredMethods();

        List<String> publicMethodNames = Arrays.stream(methods)
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toList());

        // Assert: no public method name contains update/delete keywords
        boolean hasMutationMethod = publicMethodNames.stream()
                .anyMatch(name -> name.toLowerCase().contains(keyword.toLowerCase()));

        assertThat(hasMutationMethod)
                .as("AuditService must not have any public method containing '%s'. " +
                        "Public methods found: %s", keyword, publicMethodNames)
                .isFalse();
    }

    // =====================================================================
    // Property: Created audit events SHALL retain their original field values
    // =====================================================================

    /**
     * For any valid audit event input, once created through AuditService, the
     * returned event's fields SHALL match exactly the values provided at creation.
     * The audit event is immutable—fields cannot change after construction.
     *
     * <p><b>Validates: Requirements 17.3</b></p>
     */
    @Property(tries = 100)
    void createdAuditEventFieldsShallRemainUnchanged(
            @ForAll("eventTypes") String eventType,
            @ForAll("entityTypes") String targetEntityType,
            @ForAll("jsonValues") String previousValue,
            @ForAll("jsonValues") String newValue
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        UUID targetEntityId = UUID.randomUUID();

        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            // Simulate JPA setting the ID and timestamp
            try {
                var idField = AuditEvent.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(event, UUID.randomUUID());

                var createdAtField = AuditEvent.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(event, Instant.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return event;
        });

        AuditService auditService = new AuditService(repository);

        // Act
        AuditEvent created = auditService.createAuditEvent(
                propertyId, eventType, actingUserId,
                targetEntityType, targetEntityId,
                previousValue, newValue
        );

        // Assert: all fields match the creation inputs
        assertThat(created.getPropertyId()).isEqualTo(propertyId);
        assertThat(created.getEventType()).isEqualTo(eventType);
        assertThat(created.getActingUserId()).isEqualTo(actingUserId);
        assertThat(created.getTargetEntityType()).isEqualTo(targetEntityType);
        assertThat(created.getTargetEntityId()).isEqualTo(targetEntityId);
        assertThat(created.getPreviousValue()).isEqualTo(previousValue);
        assertThat(created.getNewValue()).isEqualTo(newValue);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();

        // Verify: repository.save() was called exactly once (append-only)
        verify(repository, times(1)).save(any(AuditEvent.class));
        // Verify: no delete or deleteAll was called
        verify(repository, never()).delete(any(AuditEvent.class));
        verify(repository, never()).deleteById(any());
        verify(repository, never()).deleteAll();
    }

    // =====================================================================
    // Property: AuditEvent entity constructed values SHALL be final (cannot change)
    // =====================================================================

    /**
     * For any AuditEvent constructed with given values, reading the fields after
     * construction SHALL always return the original values. There is no mechanism
     * to alter the state post-construction.
     *
     * <p><b>Validates: Requirements 17.3</b></p>
     */
    @Property(tries = 100)
    void auditEventConstructedValuesShallBeImmutable(
            @ForAll("eventTypes") String eventType,
            @ForAll("entityTypes") String targetEntityType,
            @ForAll("jsonValues") String previousValue,
            @ForAll("jsonValues") String newValue
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        UUID targetEntityId = UUID.randomUUID();

        // Act: create the entity directly
        AuditEvent event = new AuditEvent(
                propertyId, eventType, actingUserId,
                targetEntityType, targetEntityId,
                previousValue, newValue
        );

        // Assert: fields are set to construction values
        assertThat(event.getPropertyId()).isEqualTo(propertyId);
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getActingUserId()).isEqualTo(actingUserId);
        assertThat(event.getTargetEntityType()).isEqualTo(targetEntityType);
        assertThat(event.getTargetEntityId()).isEqualTo(targetEntityId);
        assertThat(event.getPreviousValue()).isEqualTo(previousValue);
        assertThat(event.getNewValue()).isEqualTo(newValue);

        // Read the values a second time to confirm they haven't changed
        assertThat(event.getPropertyId()).isEqualTo(propertyId);
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getActingUserId()).isEqualTo(actingUserId);
        assertThat(event.getTargetEntityType()).isEqualTo(targetEntityType);
        assertThat(event.getTargetEntityId()).isEqualTo(targetEntityId);
        assertThat(event.getPreviousValue()).isEqualTo(previousValue);
        assertThat(event.getNewValue()).isEqualTo(newValue);
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    @Provide
    Arbitrary<String> criticalFields() {
        return Arbitraries.of(CRITICAL_FIELDS.toArray(new String[0]));
    }

    @Provide
    Arbitrary<String> mutationKeywords() {
        return Arbitraries.of(
                "update", "delete", "remove", "modify",
                "edit", "patch", "set", "clear", "purge"
        );
    }

    @Provide
    Arbitrary<String> eventTypes() {
        return Arbitraries.of(
                "TICKET_CREATED", "STATUS_CHANGED", "ASSIGNMENT_CREATED",
                "PRIORITY_CHANGED", "CATEGORY_CHANGED", "MEMBERSHIP_CHANGED",
                "SLA_POLICY_CHANGED", "PROPERTY_UPDATED", "PROPERTY_DEACTIVATED",
                "TICKET_ACKNOWLEDGED", "TICKET_RESOLVED", "TICKET_CLOSED"
        );
    }

    @Provide
    Arbitrary<String> entityTypes() {
        return Arbitraries.of(
                "Ticket", "Property", "Block", "Unit",
                "Membership", "SlaPolicy", "Vendor", "WorkOrder"
        );
    }

    @Provide
    Arbitrary<String> jsonValues() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of(
                        "{\"status\":\"SUBMITTED\"}",
                        "{\"status\":\"ACKNOWLEDGED\"}",
                        "{\"status\":\"IN_PROGRESS\"}",
                        "{\"status\":\"RESOLVED\"}",
                        "{\"priority\":\"LOW\"}",
                        "{\"priority\":\"HIGH\"}",
                        "{\"priority\":\"EMERGENCY\"}",
                        "{\"category\":\"PLUMBING\"}",
                        "{\"category\":\"ELECTRICAL\"}",
                        "{\"name\":\"Block A\"}",
                        "{\"role\":\"PROPERTY_MANAGER\"}"
                )
        );
    }
}
