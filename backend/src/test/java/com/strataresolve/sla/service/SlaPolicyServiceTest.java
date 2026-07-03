package com.strataresolve.sla.service;

import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.PropertyConfigChangedEvent;
import com.strataresolve.shared.exception.DuplicateResourceException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.sla.domain.SlaPolicy;
import com.strataresolve.sla.dto.CreateSlaPolicyRequest;
import com.strataresolve.sla.dto.UpdateSlaPolicyRequest;
import com.strataresolve.sla.repository.SlaPolicyRepository;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlaPolicyService")
class SlaPolicyServiceTest {

    @Mock
    private SlaPolicyRepository slaPolicyRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private SlaPolicyService slaPolicyService;

    private UUID propertyId;
    private UUID actingUserId;

    @BeforeEach
    void setUp() {
        slaPolicyService = new SlaPolicyService(slaPolicyRepository, eventPublisher);
        propertyId = UUID.randomUUID();
        actingUserId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("should create a specific SLA policy successfully")
        void shouldCreateSpecificPolicy() {
            CreateSlaPolicyRequest request = new CreateSlaPolicyRequest(
                    Category.PLUMBING, Priority.HIGH, 4, 24, false);

            when(slaPolicyRepository.existsByPropertyIdAndCategoryAndPriority(
                    propertyId, Category.PLUMBING, Priority.HIGH)).thenReturn(false);
            when(slaPolicyRepository.save(any(SlaPolicy.class))).thenAnswer(invocation -> {
                SlaPolicy p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            SlaPolicy result = slaPolicyService.create(propertyId, request, actingUserId);

            assertThat(result).isNotNull();
            assertThat(result.getCategory()).isEqualTo(Category.PLUMBING);
            assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
            assertThat(result.getAcknowledgementHours()).isEqualTo(4);
            assertThat(result.getResolutionHours()).isEqualTo(24);
            assertThat(result.getIsDefault()).isFalse();
            assertThat(result.getPropertyId()).isEqualTo(propertyId);

            verify(eventPublisher).publish(any(PropertyConfigChangedEvent.class));
        }

        @Test
        @DisplayName("should create a default SLA policy successfully")
        void shouldCreateDefaultPolicy() {
            CreateSlaPolicyRequest request = new CreateSlaPolicyRequest(
                    null, null, 8, 48, true);

            when(slaPolicyRepository.existsByPropertyIdAndIsDefaultTrue(propertyId)).thenReturn(false);
            when(slaPolicyRepository.save(any(SlaPolicy.class))).thenAnswer(invocation -> {
                SlaPolicy p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            SlaPolicy result = slaPolicyService.create(propertyId, request, actingUserId);

            assertThat(result).isNotNull();
            assertThat(result.getCategory()).isNull();
            assertThat(result.getPriority()).isNull();
            assertThat(result.getIsDefault()).isTrue();
        }

        @Test
        @DisplayName("should reject creating a second default policy for the same property")
        void shouldRejectDuplicateDefaultPolicy() {
            CreateSlaPolicyRequest request = new CreateSlaPolicyRequest(
                    null, null, 8, 48, true);

            when(slaPolicyRepository.existsByPropertyIdAndIsDefaultTrue(propertyId)).thenReturn(true);

            assertThatThrownBy(() -> slaPolicyService.create(propertyId, request, actingUserId))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("default SLA policy already exists");

            verify(slaPolicyRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should reject duplicate category/priority combination")
        void shouldRejectDuplicateCategoryPriority() {
            CreateSlaPolicyRequest request = new CreateSlaPolicyRequest(
                    Category.ELECTRICAL, Priority.URGENT, 2, 12, false);

            when(slaPolicyRepository.existsByPropertyIdAndCategoryAndPriority(
                    propertyId, Category.ELECTRICAL, Priority.URGENT)).thenReturn(true);

            assertThatThrownBy(() -> slaPolicyService.create(propertyId, request, actingUserId))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("same category and priority");

            verify(slaPolicyRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("should update policy hours successfully")
        void shouldUpdateHours() {
            UUID policyId = UUID.randomUUID();
            SlaPolicy existing = SlaPolicy.builder()
                    .id(policyId)
                    .category(Category.PLUMBING)
                    .priority(Priority.HIGH)
                    .acknowledgementHours(4)
                    .resolutionHours(24)
                    .isDefault(false)
                    .build();
            existing.setPropertyId(propertyId);

            UpdateSlaPolicyRequest request = new UpdateSlaPolicyRequest(
                    Category.PLUMBING, Priority.HIGH, 2, 12, false);

            when(slaPolicyRepository.findById(policyId)).thenReturn(Optional.of(existing));
            when(slaPolicyRepository.save(any(SlaPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

            SlaPolicy result = slaPolicyService.update(policyId, propertyId, request, actingUserId);

            assertThat(result.getAcknowledgementHours()).isEqualTo(2);
            assertThat(result.getResolutionHours()).isEqualTo(12);
            verify(eventPublisher).publish(any(PropertyConfigChangedEvent.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when policy does not exist")
        void shouldThrowWhenNotFound() {
            UUID policyId = UUID.randomUUID();
            UpdateSlaPolicyRequest request = new UpdateSlaPolicyRequest(
                    Category.PLUMBING, Priority.HIGH, 2, 12, false);

            when(slaPolicyRepository.findById(policyId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> slaPolicyService.update(policyId, propertyId, request, actingUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("should delete existing policy and publish event")
        void shouldDeletePolicy() {
            UUID policyId = UUID.randomUUID();
            SlaPolicy existing = SlaPolicy.builder()
                    .id(policyId)
                    .category(Category.PLUMBING)
                    .priority(Priority.HIGH)
                    .acknowledgementHours(4)
                    .resolutionHours(24)
                    .isDefault(false)
                    .build();
            existing.setPropertyId(propertyId);

            when(slaPolicyRepository.findById(policyId)).thenReturn(Optional.of(existing));

            slaPolicyService.delete(policyId, propertyId, actingUserId);

            verify(slaPolicyRepository).delete(existing);

            ArgumentCaptor<PropertyConfigChangedEvent> eventCaptor =
                    ArgumentCaptor.forClass(PropertyConfigChangedEvent.class);
            verify(eventPublisher).publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getAction()).isEqualTo("DELETED");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when deleting non-existent policy")
        void shouldThrowWhenDeletingNotFound() {
            UUID policyId = UUID.randomUUID();
            when(slaPolicyRepository.findById(policyId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> slaPolicyService.delete(policyId, propertyId, actingUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("resolvePolicy - fallback logic")
    class ResolvePolicyTests {

        @Test
        @DisplayName("should return exact match when specific policy exists for property/category/priority")
        void shouldReturnExactMatch() {
            SlaPolicy exactPolicy = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.PLUMBING)
                    .priority(Priority.HIGH)
                    .acknowledgementHours(2)
                    .resolutionHours(8)
                    .isDefault(false)
                    .build();
            exactPolicy.setPropertyId(propertyId);

            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriority(
                    propertyId, Category.PLUMBING, Priority.HIGH))
                    .thenReturn(Optional.of(exactPolicy));

            Optional<SlaPolicy> result = slaPolicyService.resolvePolicy(
                    propertyId, Category.PLUMBING, Priority.HIGH);

            assertThat(result).isPresent();
            assertThat(result.get().getAcknowledgementHours()).isEqualTo(2);
            assertThat(result.get().getResolutionHours()).isEqualTo(8);
        }

        @Test
        @DisplayName("should fall back to category-level default when no exact match")
        void shouldFallBackToCategoryDefault() {
            SlaPolicy categoryDefault = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.PLUMBING)
                    .priority(null)
                    .acknowledgementHours(4)
                    .resolutionHours(16)
                    .isDefault(false)
                    .build();
            categoryDefault.setPropertyId(propertyId);

            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriority(
                    propertyId, Category.PLUMBING, Priority.HIGH))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriorityIsNull(
                    propertyId, Category.PLUMBING))
                    .thenReturn(Optional.of(categoryDefault));

            Optional<SlaPolicy> result = slaPolicyService.resolvePolicy(
                    propertyId, Category.PLUMBING, Priority.HIGH);

            assertThat(result).isPresent();
            assertThat(result.get().getAcknowledgementHours()).isEqualTo(4);
            assertThat(result.get().getCategory()).isEqualTo(Category.PLUMBING);
            assertThat(result.get().getPriority()).isNull();
        }

        @Test
        @DisplayName("should fall back to priority-level default when no exact or category match")
        void shouldFallBackToPriorityDefault() {
            SlaPolicy priorityDefault = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(null)
                    .priority(Priority.HIGH)
                    .acknowledgementHours(3)
                    .resolutionHours(12)
                    .isDefault(false)
                    .build();
            priorityDefault.setPropertyId(propertyId);

            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriority(
                    propertyId, Category.PLUMBING, Priority.HIGH))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriorityIsNull(
                    propertyId, Category.PLUMBING))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndCategoryIsNullAndPriority(
                    propertyId, Priority.HIGH))
                    .thenReturn(Optional.of(priorityDefault));

            Optional<SlaPolicy> result = slaPolicyService.resolvePolicy(
                    propertyId, Category.PLUMBING, Priority.HIGH);

            assertThat(result).isPresent();
            assertThat(result.get().getAcknowledgementHours()).isEqualTo(3);
            assertThat(result.get().getCategory()).isNull();
            assertThat(result.get().getPriority()).isEqualTo(Priority.HIGH);
        }

        @Test
        @DisplayName("should fall back to property default when no specific matches")
        void shouldFallBackToPropertyDefault() {
            SlaPolicy defaultPolicy = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(null)
                    .priority(null)
                    .acknowledgementHours(8)
                    .resolutionHours(48)
                    .isDefault(true)
                    .build();
            defaultPolicy.setPropertyId(propertyId);

            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriority(
                    propertyId, Category.PLUMBING, Priority.HIGH))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriorityIsNull(
                    propertyId, Category.PLUMBING))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndCategoryIsNullAndPriority(
                    propertyId, Priority.HIGH))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndIsDefaultTrue(propertyId))
                    .thenReturn(Optional.of(defaultPolicy));

            Optional<SlaPolicy> result = slaPolicyService.resolvePolicy(
                    propertyId, Category.PLUMBING, Priority.HIGH);

            assertThat(result).isPresent();
            assertThat(result.get().getIsDefault()).isTrue();
            assertThat(result.get().getAcknowledgementHours()).isEqualTo(8);
        }

        @Test
        @DisplayName("should return empty when no policy exists at any level")
        void shouldReturnEmptyWhenNoPolicyExists() {
            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriority(
                    propertyId, Category.PLUMBING, Priority.HIGH))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriorityIsNull(
                    propertyId, Category.PLUMBING))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndCategoryIsNullAndPriority(
                    propertyId, Priority.HIGH))
                    .thenReturn(Optional.empty());
            when(slaPolicyRepository.findByPropertyIdAndIsDefaultTrue(propertyId))
                    .thenReturn(Optional.empty());

            Optional<SlaPolicy> result = slaPolicyService.resolvePolicy(
                    propertyId, Category.PLUMBING, Priority.HIGH);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should prefer exact match over category default")
        void shouldPreferExactOverCategoryDefault() {
            SlaPolicy exactPolicy = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.ELECTRICAL)
                    .priority(Priority.URGENT)
                    .acknowledgementHours(1)
                    .resolutionHours(4)
                    .isDefault(false)
                    .build();
            exactPolicy.setPropertyId(propertyId);

            when(slaPolicyRepository.findByPropertyIdAndCategoryAndPriority(
                    propertyId, Category.ELECTRICAL, Priority.URGENT))
                    .thenReturn(Optional.of(exactPolicy));

            Optional<SlaPolicy> result = slaPolicyService.resolvePolicy(
                    propertyId, Category.ELECTRICAL, Priority.URGENT);

            assertThat(result).isPresent();
            assertThat(result.get().getAcknowledgementHours()).isEqualTo(1);
            assertThat(result.get().getCategory()).isEqualTo(Category.ELECTRICAL);
            assertThat(result.get().getPriority()).isEqualTo(Priority.URGENT);
        }
    }

    @Nested
    @DisplayName("findByPropertyId")
    class FindByPropertyIdTests {

        @Test
        @DisplayName("should return all policies for a property")
        void shouldReturnAllPolicies() {
            SlaPolicy p1 = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.PLUMBING)
                    .priority(Priority.HIGH)
                    .acknowledgementHours(2)
                    .resolutionHours(8)
                    .isDefault(false)
                    .build();
            p1.setPropertyId(propertyId);

            SlaPolicy p2 = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(null)
                    .priority(null)
                    .acknowledgementHours(8)
                    .resolutionHours(48)
                    .isDefault(true)
                    .build();
            p2.setPropertyId(propertyId);

            when(slaPolicyRepository.findByPropertyId(propertyId)).thenReturn(List.of(p1, p2));

            List<SlaPolicy> result = slaPolicyService.findByPropertyId(propertyId);

            assertThat(result).hasSize(2);
        }
    }
}
