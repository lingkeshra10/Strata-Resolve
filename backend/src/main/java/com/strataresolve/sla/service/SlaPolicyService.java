package com.strataresolve.sla.service;

import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.PropertyConfigChangedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.DuplicateResourceException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.sla.domain.SlaPolicy;
import com.strataresolve.sla.dto.CreateSlaPolicyRequest;
import com.strataresolve.sla.dto.UpdateSlaPolicyRequest;
import com.strataresolve.sla.repository.SlaPolicyRepository;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing SLA policy CRUD operations and policy lookup with fallback logic.
 *
 * <p>Policy resolution order:
 * <ol>
 *   <li>Exact match: property + category + priority</li>
 *   <li>Category-level default: property + category + null priority</li>
 *   <li>Priority-level default: property + null category + priority</li>
 *   <li>Property default: property + is_default = true</li>
 * </ol>
 */
@Service
@Transactional
public class SlaPolicyService {

    private final SlaPolicyRepository slaPolicyRepository;
    private final DomainEventPublisher eventPublisher;

    public SlaPolicyService(SlaPolicyRepository slaPolicyRepository,
                            DomainEventPublisher eventPublisher) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new SLA policy for the given property.
     *
     * @param propertyId the property to create the policy for
     * @param request the creation request
     * @param actingUserId the user performing the action
     * @return the created SLA policy
     * @throws DuplicateResourceException if a policy with the same category/priority already exists
     * @throws BusinessRuleViolationException if a default policy already exists when creating another default
     */
    public SlaPolicy create(UUID propertyId, CreateSlaPolicyRequest request, UUID actingUserId) {
        validateNoDuplicate(propertyId, request.category(), request.priority(), request.isDefault());

        SlaPolicy policy = SlaPolicy.builder()
                .category(request.category())
                .priority(request.priority())
                .acknowledgementHours(request.acknowledgementHours())
                .resolutionHours(request.resolutionHours())
                .isDefault(request.isDefault())
                .build();
        policy.setPropertyId(propertyId);

        SlaPolicy saved = slaPolicyRepository.save(policy);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "SlaPolicy", saved.getId(),
                "CREATED", null, formatPolicySummary(saved)
        ));

        return saved;
    }

    /**
     * Updates an existing SLA policy.
     *
     * @param policyId the policy to update
     * @param propertyId the property context
     * @param request the update request
     * @param actingUserId the user performing the action
     * @return the updated SLA policy
     */
    public SlaPolicy update(UUID policyId, UUID propertyId, UpdateSlaPolicyRequest request, UUID actingUserId) {
        SlaPolicy policy = findByIdOrThrow(policyId);

        // If changing category/priority/default, validate no duplicate
        boolean categoryChanged = !equalNullable(policy.getCategory(), request.category());
        boolean priorityChanged = !equalNullable(policy.getPriority(), request.priority());
        boolean defaultChanged = !policy.getIsDefault().equals(request.isDefault());

        if (categoryChanged || priorityChanged || defaultChanged) {
            validateNoDuplicateExcluding(propertyId, request.category(), request.priority(),
                    request.isDefault(), policyId);
        }

        String previousValue = formatPolicySummary(policy);

        policy.setCategory(request.category());
        policy.setPriority(request.priority());
        policy.setAcknowledgementHours(request.acknowledgementHours());
        policy.setResolutionHours(request.resolutionHours());
        policy.setIsDefault(request.isDefault());

        SlaPolicy saved = slaPolicyRepository.save(policy);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "SlaPolicy", saved.getId(),
                "UPDATED", previousValue, formatPolicySummary(saved)
        ));

        return saved;
    }

    /**
     * Finds an SLA policy by its ID.
     *
     * @param policyId the policy ID
     * @return the SLA policy
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public SlaPolicy findById(UUID policyId) {
        return findByIdOrThrow(policyId);
    }

    /**
     * Finds all SLA policies for a property.
     *
     * @param propertyId the property ID
     * @return list of policies
     */
    @Transactional(readOnly = true)
    public List<SlaPolicy> findByPropertyId(UUID propertyId) {
        return slaPolicyRepository.findByPropertyId(propertyId);
    }

    /**
     * Deletes an SLA policy.
     *
     * @param policyId the policy to delete
     * @param propertyId the property context
     * @param actingUserId the user performing the action
     */
    public void delete(UUID policyId, UUID propertyId, UUID actingUserId) {
        SlaPolicy policy = findByIdOrThrow(policyId);

        String previousValue = formatPolicySummary(policy);
        slaPolicyRepository.delete(policy);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "SlaPolicy", policyId,
                "DELETED", previousValue, null
        ));
    }

    /**
     * Resolves the applicable SLA policy for a given property, category, and priority.
     * Uses a fallback chain:
     * <ol>
     *   <li>Exact match (property + category + priority)</li>
     *   <li>Category-level default (property + category + null priority)</li>
     *   <li>Priority-level default (property + null category + priority)</li>
     *   <li>Property default (is_default = true)</li>
     * </ol>
     *
     * @param propertyId the property
     * @param category the ticket category
     * @param priority the ticket priority
     * @return the resolved policy, or empty if no policy exists
     */
    @Transactional(readOnly = true)
    public Optional<SlaPolicy> resolvePolicy(UUID propertyId, Category category, Priority priority) {
        // 1. Exact match
        Optional<SlaPolicy> exact = slaPolicyRepository
                .findByPropertyIdAndCategoryAndPriority(propertyId, category, priority);
        if (exact.isPresent()) {
            return exact;
        }

        // 2. Category-level default (matching category, any priority)
        Optional<SlaPolicy> categoryDefault = slaPolicyRepository
                .findByPropertyIdAndCategoryAndPriorityIsNull(propertyId, category);
        if (categoryDefault.isPresent()) {
            return categoryDefault;
        }

        // 3. Priority-level default (matching priority, any category)
        Optional<SlaPolicy> priorityDefault = slaPolicyRepository
                .findByPropertyIdAndCategoryIsNullAndPriority(propertyId, priority);
        if (priorityDefault.isPresent()) {
            return priorityDefault;
        }

        // 4. Property default
        return slaPolicyRepository.findByPropertyIdAndIsDefaultTrue(propertyId);
    }

    private SlaPolicy findByIdOrThrow(UUID policyId) {
        return slaPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("SlaPolicy", policyId));
    }

    private void validateNoDuplicate(UUID propertyId, Category category, Priority priority, Boolean isDefault) {
        if (Boolean.TRUE.equals(isDefault) && slaPolicyRepository.existsByPropertyIdAndIsDefaultTrue(propertyId)) {
            throw new DuplicateResourceException(
                    "A default SLA policy already exists for this property");
        }

        if (category != null || priority != null) {
            if (slaPolicyRepository.existsByPropertyIdAndCategoryAndPriority(propertyId, category, priority)) {
                throw new DuplicateResourceException(
                        "An SLA policy with the same category and priority combination already exists");
            }
        }
    }

    private void validateNoDuplicateExcluding(UUID propertyId, Category category, Priority priority,
                                              Boolean isDefault, UUID excludeId) {
        if (Boolean.TRUE.equals(isDefault)) {
            Optional<SlaPolicy> existing = slaPolicyRepository.findByPropertyIdAndIsDefaultTrue(propertyId);
            if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
                throw new DuplicateResourceException(
                        "A default SLA policy already exists for this property");
            }
        }

        if (category != null || priority != null) {
            Optional<SlaPolicy> existing = slaPolicyRepository
                    .findByPropertyIdAndCategoryAndPriority(propertyId, category, priority);
            if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
                throw new DuplicateResourceException(
                        "An SLA policy with the same category and priority combination already exists");
            }
        }
    }

    private boolean equalNullable(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private String formatPolicySummary(SlaPolicy policy) {
        return String.format(
                "{\"category\":\"%s\",\"priority\":\"%s\",\"acknowledgementHours\":%d,\"resolutionHours\":%d,\"isDefault\":%s}",
                policy.getCategory() != null ? policy.getCategory() : "null",
                policy.getPriority() != null ? policy.getPriority() : "null",
                policy.getAcknowledgementHours(),
                policy.getResolutionHours(),
                policy.getIsDefault()
        );
    }
}
