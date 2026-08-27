package com.strataresolve.identity.service;


import com.strataresolve.common.exception.BusinessRuleViolationException;
import com.strataresolve.common.exception.DuplicateResourceException;
import com.strataresolve.common.exception.ResourceNotFoundException;
import com.strataresolve.common.tenant.MembershipCheckRepository;
import com.strataresolve.identity.domain.Membership;
import com.strataresolve.identity.domain.Role;
import com.strataresolve.identity.dto.CreateMembershipRequest;
import com.strataresolve.identity.repository.MembershipRepository;
import com.strataresolve.identity.repository.UserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing membership lifecycle: role assignment, validation,
 * unit linkage, and activation/deactivation.
 *
 * Also implements {@link MembershipCheckRepository} so the TenantContextFilter
 * can use the JPA-backed membership checks instead of raw JDBC.
 * Marked as {@code @Primary} to take precedence over the JDBC-based fallback.
 */
@Service
@Primary
public class MembershipService implements MembershipCheckRepository {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public MembershipService(MembershipRepository membershipRepository, UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new membership linking a user to a property with a specified role.
     *
     * @param request the membership creation details
     * @return the created Membership entity
     * @throws ResourceNotFoundException if the user does not exist
     * @throws DuplicateResourceException if the user already has an active membership with the same role for the property
     * @throws BusinessRuleViolationException if a resident role is specified without a unit ID
     */
    @Transactional
    public Membership createMembership(CreateMembershipRequest request) {
        // Validate user exists
        if (!userRepository.existsById(request.getUserId())) {
            throw new ResourceNotFoundException("User", request.getUserId());
        }

        // Check for duplicate active membership with the same role
        if (membershipRepository.existsActiveByUserIdAndPropertyIdAndRole(
                request.getUserId(), request.getPropertyId(), request.getRole())) {
            throw new DuplicateResourceException(
                    "User already has an active " + request.getRole() + " membership for this property");
        }

        // Validate unit linkage for resident roles
        if (isResidentRole(request.getRole()) && request.getUnitId() == null) {
            throw new BusinessRuleViolationException(
                    "A unit ID is required for resident roles (" + request.getRole() + ")");
        }

        LocalDate effectiveFrom = request.getEffectiveFrom() != null
                ? request.getEffectiveFrom()
                : LocalDate.now();

        Membership membership = Membership.builder()
                .userId(request.getUserId())
                .unitId(request.getUnitId())
                .role(request.getRole())
                .isActive(true)
                .effectiveFrom(effectiveFrom)
                .build();
        membership.setPropertyId(request.getPropertyId());

        return membershipRepository.save(membership);
    }

    /**
     * Deactivates a membership by setting is_active to false and recording the effective_to date.
     * The record is preserved for historical purposes — it is never deleted.
     *
     * @param membershipId the ID of the membership to deactivate
     * @return the deactivated Membership entity
     * @throws ResourceNotFoundException if the membership does not exist
     * @throws BusinessRuleViolationException if the membership is already inactive
     */
    @Transactional
    public Membership deactivateMembership(UUID membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        if (!membership.isActive()) {
            throw new BusinessRuleViolationException("Membership is already inactive");
        }

        membership.deactivate();
        return membershipRepository.save(membership);
    }

    /**
     * Returns all active memberships for a user within a specific property.
     */
    @Transactional(readOnly = true)
    public List<Membership> getActiveMemberships(UUID userId, UUID propertyId) {
        return membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId);
    }

    /**
     * Returns all active memberships for a user across all properties.
     */
    @Transactional(readOnly = true)
    public List<Membership> getActiveMembershipsByUser(UUID userId) {
        return membershipRepository.findActiveByUserId(userId);
    }

    /**
     * Checks whether a user has an active membership for a given property.
     * Implements {@link MembershipCheckRepository#hasActiveMembership} for the TenantContextFilter.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveMembership(UUID userId, UUID propertyId) {
        return membershipRepository.hasActiveMembership(userId, propertyId);
    }

    /**
     * Links a resident membership to a specific unit, creating a unit occupancy record
     * with the effective date.
     *
     * @param membershipId the membership to link
     * @param unitId the unit to link to
     * @return the updated Membership entity
     * @throws ResourceNotFoundException if the membership does not exist
     * @throws BusinessRuleViolationException if the membership is not a resident role or is inactive
     */
    @Transactional
    public Membership linkResidentToUnit(UUID membershipId, UUID unitId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        if (!membership.isActive()) {
            throw new BusinessRuleViolationException("Cannot link unit to an inactive membership");
        }

        if (!isResidentRole(membership.getRole())) {
            throw new BusinessRuleViolationException(
                    "Only resident roles can be linked to a unit. Current role: " + membership.getRole());
        }

        membership.linkToUnit(unitId);
        return membershipRepository.save(membership);
    }

    /**
     * Checks if the given role is a resident role (RESIDENT_OWNER or RESIDENT_TENANT).
     */
    private boolean isResidentRole(Role role) {
        return role == Role.RESIDENT_OWNER || role == Role.RESIDENT_TENANT;
    }
}
