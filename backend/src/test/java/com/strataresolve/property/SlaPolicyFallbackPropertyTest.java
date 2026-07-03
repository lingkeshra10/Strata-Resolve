package com.strataresolve.property;

import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.sla.domain.SlaPolicy;
import com.strataresolve.sla.repository.SlaPolicyRepository;
import com.strataresolve.sla.service.SlaPolicyService;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import net.jqwik.api.*;
import net.jqwik.api.Combinators;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based test for SLA Policy Fallback.
 *
 * <p><b>Property 7: SLA Policy Fallback</b></p>
 * <p>For any ticket whose category and priority combination has no specific SLA policy defined,
 * the system SHALL apply the default SLA policy for the property. If a specific policy exists,
 * it SHALL take precedence over the default.</p>
 *
 * <p><b>Validates: Requirements 14.5</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 7: SLA Policy Fallback")
class SlaPolicyFallbackPropertyTest {

    // =====================================================================
    // Property: Exact match takes precedence over all fallback levels
    // =====================================================================

    /**
     * For any property, category, and priority combination where an exact-match policy exists,
     * resolvePolicy SHALL return that exact-match policy, regardless of whether category-level,
     * priority-level, or property-default policies also exist.
     *
     * <p><b>Validates: Requirements 14.5</b></p>
     */
    @Property(tries = 100)
    void exactMatchPolicyTakesPrecedenceOverAllFallbacks(
            @ForAll("categoryArbitrary") Category category,
            @ForAll("priorityArbitrary") Priority priority
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        SlaPolicyRepository repository = mock(SlaPolicyRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SlaPolicyService service = new SlaPolicyService(repository, eventPublisher);

        SlaPolicy exactPolicy = buildPolicy(propertyId, category, priority, false, 4, 24);
        SlaPolicy categoryDefault = buildPolicy(propertyId, category, null, false, 8, 48);
        SlaPolicy priorityDefault = buildPolicy(propertyId, null, priority, false, 12, 72);
        SlaPolicy propertyDefault = buildPolicy(propertyId, null, null, true, 24, 168);

        when(repository.findByPropertyIdAndCategoryAndPriority(propertyId, category, priority))
                .thenReturn(Optional.of(exactPolicy));
        when(repository.findByPropertyIdAndCategoryAndPriorityIsNull(propertyId, category))
                .thenReturn(Optional.of(categoryDefault));
        when(repository.findByPropertyIdAndCategoryIsNullAndPriority(propertyId, priority))
                .thenReturn(Optional.of(priorityDefault));
        when(repository.findByPropertyIdAndIsDefaultTrue(propertyId))
                .thenReturn(Optional.of(propertyDefault));

        // Act
        Optional<SlaPolicy> result = service.resolvePolicy(propertyId, category, priority);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(exactPolicy);
        assertThat(result.get().getCategory()).isEqualTo(category);
        assertThat(result.get().getPriority()).isEqualTo(priority);

        // Verify no lower-level fallback queries were invoked
        verify(repository, never()).findByPropertyIdAndCategoryAndPriorityIsNull(any(), any());
        verify(repository, never()).findByPropertyIdAndCategoryIsNullAndPriority(any(), any());
        verify(repository, never()).findByPropertyIdAndIsDefaultTrue(any());
    }

    // =====================================================================
    // Property: Category-level default is used when no exact match exists
    // =====================================================================

    /**
     * For any property, category, and priority combination where no exact-match policy exists
     * but a category-level default (category + null priority) does, resolvePolicy SHALL return
     * the category-level default policy.
     *
     * <p><b>Validates: Requirements 14.5</b></p>
     */
    @Property(tries = 100)
    void categoryLevelDefaultUsedWhenNoExactMatch(
            @ForAll("categoryArbitrary") Category category,
            @ForAll("priorityArbitrary") Priority priority
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        SlaPolicyRepository repository = mock(SlaPolicyRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SlaPolicyService service = new SlaPolicyService(repository, eventPublisher);

        SlaPolicy categoryDefault = buildPolicy(propertyId, category, null, false, 8, 48);
        SlaPolicy priorityDefault = buildPolicy(propertyId, null, priority, false, 12, 72);
        SlaPolicy propertyDefault = buildPolicy(propertyId, null, null, true, 24, 168);

        when(repository.findByPropertyIdAndCategoryAndPriority(propertyId, category, priority))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndCategoryAndPriorityIsNull(propertyId, category))
                .thenReturn(Optional.of(categoryDefault));
        when(repository.findByPropertyIdAndCategoryIsNullAndPriority(propertyId, priority))
                .thenReturn(Optional.of(priorityDefault));
        when(repository.findByPropertyIdAndIsDefaultTrue(propertyId))
                .thenReturn(Optional.of(propertyDefault));

        // Act
        Optional<SlaPolicy> result = service.resolvePolicy(propertyId, category, priority);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(categoryDefault);
        assertThat(result.get().getCategory()).isEqualTo(category);
        assertThat(result.get().getPriority()).isNull();

        // Verify priority-level and property-level were not queried
        verify(repository, never()).findByPropertyIdAndCategoryIsNullAndPriority(any(), any());
        verify(repository, never()).findByPropertyIdAndIsDefaultTrue(any());
    }

    // =====================================================================
    // Property: Priority-level default is used when no exact or category match
    // =====================================================================

    /**
     * For any property, category, and priority combination where no exact-match or
     * category-level default exists, but a priority-level default (null category + priority)
     * does, resolvePolicy SHALL return the priority-level default policy.
     *
     * <p><b>Validates: Requirements 14.5</b></p>
     */
    @Property(tries = 100)
    void priorityLevelDefaultUsedWhenNoExactOrCategoryMatch(
            @ForAll("categoryArbitrary") Category category,
            @ForAll("priorityArbitrary") Priority priority
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        SlaPolicyRepository repository = mock(SlaPolicyRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SlaPolicyService service = new SlaPolicyService(repository, eventPublisher);

        SlaPolicy priorityDefault = buildPolicy(propertyId, null, priority, false, 12, 72);
        SlaPolicy propertyDefault = buildPolicy(propertyId, null, null, true, 24, 168);

        when(repository.findByPropertyIdAndCategoryAndPriority(propertyId, category, priority))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndCategoryAndPriorityIsNull(propertyId, category))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndCategoryIsNullAndPriority(propertyId, priority))
                .thenReturn(Optional.of(priorityDefault));
        when(repository.findByPropertyIdAndIsDefaultTrue(propertyId))
                .thenReturn(Optional.of(propertyDefault));

        // Act
        Optional<SlaPolicy> result = service.resolvePolicy(propertyId, category, priority);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(priorityDefault);
        assertThat(result.get().getCategory()).isNull();
        assertThat(result.get().getPriority()).isEqualTo(priority);

        // Verify property-level default was not queried
        verify(repository, never()).findByPropertyIdAndIsDefaultTrue(any());
    }

    // =====================================================================
    // Property: Property default is applied when no specific policy exists
    // =====================================================================

    /**
     * For any property, category, and priority combination where no exact-match,
     * category-level, or priority-level policy exists, resolvePolicy SHALL return
     * the property default policy (is_default = true).
     *
     * <p><b>Validates: Requirements 14.5</b></p>
     */
    @Property(tries = 100)
    void propertyDefaultAppliedWhenNoSpecificPolicyExists(
            @ForAll("categoryArbitrary") Category category,
            @ForAll("priorityArbitrary") Priority priority
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        SlaPolicyRepository repository = mock(SlaPolicyRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SlaPolicyService service = new SlaPolicyService(repository, eventPublisher);

        SlaPolicy propertyDefault = buildPolicy(propertyId, null, null, true, 24, 168);

        when(repository.findByPropertyIdAndCategoryAndPriority(propertyId, category, priority))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndCategoryAndPriorityIsNull(propertyId, category))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndCategoryIsNullAndPriority(propertyId, priority))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndIsDefaultTrue(propertyId))
                .thenReturn(Optional.of(propertyDefault));

        // Act
        Optional<SlaPolicy> result = service.resolvePolicy(propertyId, category, priority);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(propertyDefault);
        assertThat(result.get().getIsDefault()).isTrue();
    }

    // =====================================================================
    // Property: Empty result when no policy exists at any level
    // =====================================================================

    /**
     * For any property, category, and priority combination where no policy exists
     * at any fallback level, resolvePolicy SHALL return empty.
     *
     * <p><b>Validates: Requirements 14.5</b></p>
     */
    @Property(tries = 100)
    void emptyResultWhenNoPolicyExistsAtAnyLevel(
            @ForAll("categoryArbitrary") Category category,
            @ForAll("priorityArbitrary") Priority priority
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        SlaPolicyRepository repository = mock(SlaPolicyRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SlaPolicyService service = new SlaPolicyService(repository, eventPublisher);

        when(repository.findByPropertyIdAndCategoryAndPriority(propertyId, category, priority))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndCategoryAndPriorityIsNull(propertyId, category))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndCategoryIsNullAndPriority(propertyId, priority))
                .thenReturn(Optional.empty());
        when(repository.findByPropertyIdAndIsDefaultTrue(propertyId))
                .thenReturn(Optional.empty());

        // Act
        Optional<SlaPolicy> result = service.resolvePolicy(propertyId, category, priority);

        // Assert
        assertThat(result).isEmpty();
    }

    // =====================================================================
    // Property: Resolution chain is deterministic for any combination
    // =====================================================================

    /**
     * For any random subset of policies available at different fallback levels,
     * the resolution SHALL always return the highest-priority policy according to
     * the defined chain: exact > category > priority > default.
     *
     * <p><b>Validates: Requirements 14.5</b></p>
     */
    @Property(tries = 100)
    void resolutionChainIsDeterministic(
            @ForAll("fallbackScenario") FallbackScenario scenario
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        Category category = scenario.category();
        Priority priority = scenario.priority();

        SlaPolicyRepository repository = mock(SlaPolicyRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SlaPolicyService service = new SlaPolicyService(repository, eventPublisher);

        // Setup policies based on scenario flags
        SlaPolicy exactPolicy = scenario.hasExactMatch()
                ? buildPolicy(propertyId, category, priority, false, 4, 24) : null;
        SlaPolicy categoryDefault = scenario.hasCategoryDefault()
                ? buildPolicy(propertyId, category, null, false, 8, 48) : null;
        SlaPolicy priorityDefault = scenario.hasPriorityDefault()
                ? buildPolicy(propertyId, null, priority, false, 12, 72) : null;
        SlaPolicy propertyDefault = scenario.hasPropertyDefault()
                ? buildPolicy(propertyId, null, null, true, 24, 168) : null;

        when(repository.findByPropertyIdAndCategoryAndPriority(propertyId, category, priority))
                .thenReturn(Optional.ofNullable(exactPolicy));
        when(repository.findByPropertyIdAndCategoryAndPriorityIsNull(propertyId, category))
                .thenReturn(Optional.ofNullable(categoryDefault));
        when(repository.findByPropertyIdAndCategoryIsNullAndPriority(propertyId, priority))
                .thenReturn(Optional.ofNullable(priorityDefault));
        when(repository.findByPropertyIdAndIsDefaultTrue(propertyId))
                .thenReturn(Optional.ofNullable(propertyDefault));

        // Determine expected result based on resolution order
        SlaPolicy expectedPolicy;
        if (exactPolicy != null) {
            expectedPolicy = exactPolicy;
        } else if (categoryDefault != null) {
            expectedPolicy = categoryDefault;
        } else if (priorityDefault != null) {
            expectedPolicy = priorityDefault;
        } else {
            expectedPolicy = propertyDefault;
        }

        // Act
        Optional<SlaPolicy> result = service.resolvePolicy(propertyId, category, priority);

        // Assert
        if (expectedPolicy == null) {
            assertThat(result).isEmpty();
        } else {
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(expectedPolicy);
        }
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    @Provide
    Arbitrary<Category> categoryArbitrary() {
        return Arbitraries.of(Category.values());
    }

    @Provide
    Arbitrary<Priority> priorityArbitrary() {
        return Arbitraries.of(Priority.values());
    }

    @Provide
    Arbitrary<FallbackScenario> fallbackScenario() {
        return Combinators.combine(
                Arbitraries.of(Category.values()),
                Arbitraries.of(Priority.values()),
                Arbitraries.of(true, false),
                Arbitraries.of(true, false),
                Arbitraries.of(true, false),
                Arbitraries.of(true, false)
        ).as(FallbackScenario::new);
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private static SlaPolicy buildPolicy(UUID propertyId, Category category, Priority priority,
                                          boolean isDefault, int ackHours, int resHours) {
        SlaPolicy policy = SlaPolicy.builder()
                .id(UUID.randomUUID())
                .category(category)
                .priority(priority)
                .acknowledgementHours(ackHours)
                .resolutionHours(resHours)
                .isDefault(isDefault)
                .build();
        policy.setPropertyId(propertyId);
        return policy;
    }

    // =====================================================================
    // Test Data Record
    // =====================================================================

    record FallbackScenario(
            Category category,
            Priority priority,
            boolean hasExactMatch,
            boolean hasCategoryDefault,
            boolean hasPriorityDefault,
            boolean hasPropertyDefault
    ) {}
}
