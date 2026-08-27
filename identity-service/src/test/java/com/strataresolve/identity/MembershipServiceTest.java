package com.strataresolve.identity;

import com.strataresolve.common.exception.BusinessRuleViolationException;
import com.strataresolve.common.exception.DuplicateResourceException;
import com.strataresolve.common.exception.ResourceNotFoundException;
import com.strataresolve.identity.domain.Membership;
import com.strataresolve.identity.domain.Role;
import com.strataresolve.identity.dto.CreateMembershipRequest;
import com.strataresolve.identity.repository.MembershipRepository;
import com.strataresolve.identity.repository.UserRepository;
import com.strataresolve.identity.service.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    private MembershipService membershipService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        membershipService = new MembershipService(membershipRepository, userRepository);
    }

    // --- createMembership tests ---

    @Test
    void createMembership_withValidRequest_createsMembership() {
        CreateMembershipRequest request = CreateMembershipRequest.builder()
                .userId(USER_ID)
                .propertyId(PROPERTY_ID)
                .role(Role.PROPERTY_MANAGER)
                .effectiveFrom(LocalDate.of(2025, 1, 15))
                .build();

        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(membershipRepository.existsActiveByUserIdAndPropertyIdAndRole(USER_ID, PROPERTY_ID, Role.PROPERTY_MANAGER))
                .thenReturn(false);
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = membershipService.createMembership(request);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getPropertyId()).isEqualTo(PROPERTY_ID);
        assertThat(result.getRole()).isEqualTo(Role.PROPERTY_MANAGER);
        assertThat(result.isActive()).isTrue();
        assertThat(result.getEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(result.getUnitId()).isNull();
    }

    @Test
    void createMembership_withResidentRole_requiresUnitId() {
        CreateMembershipRequest request = CreateMembershipRequest.builder()
                .userId(USER_ID)
                .propertyId(PROPERTY_ID)
                .role(Role.RESIDENT_OWNER)
                .unitId(UNIT_ID)
                .effectiveFrom(LocalDate.of(2025, 1, 15))
                .build();

        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(membershipRepository.existsActiveByUserIdAndPropertyIdAndRole(USER_ID, PROPERTY_ID, Role.RESIDENT_OWNER))
                .thenReturn(false);
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = membershipService.createMembership(request);

        assertThat(result.getUnitId()).isEqualTo(UNIT_ID);
        assertThat(result.getRole()).isEqualTo(Role.RESIDENT_OWNER);
    }

    @Test
    void createMembership_withResidentRoleWithoutUnit_throwsException() {
        CreateMembershipRequest request = CreateMembershipRequest.builder()
                .userId(USER_ID)
                .propertyId(PROPERTY_ID)
                .role(Role.RESIDENT_TENANT)
                .unitId(null)
                .build();

        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(membershipRepository.existsActiveByUserIdAndPropertyIdAndRole(USER_ID, PROPERTY_ID, Role.RESIDENT_TENANT))
                .thenReturn(false);

        assertThatThrownBy(() -> membershipService.createMembership(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("unit ID is required");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void createMembership_withNonExistentUser_throwsException() {
        CreateMembershipRequest request = CreateMembershipRequest.builder()
                .userId(USER_ID)
                .propertyId(PROPERTY_ID)
                .role(Role.TECHNICIAN)
                .build();

        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> membershipService.createMembership(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void createMembership_withDuplicateActiveRole_throwsException() {
        CreateMembershipRequest request = CreateMembershipRequest.builder()
                .userId(USER_ID)
                .propertyId(PROPERTY_ID)
                .role(Role.PROPERTY_MANAGER)
                .build();

        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(membershipRepository.existsActiveByUserIdAndPropertyIdAndRole(USER_ID, PROPERTY_ID, Role.PROPERTY_MANAGER))
                .thenReturn(true);

        assertThatThrownBy(() -> membershipService.createMembership(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("PROPERTY_MANAGER");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void createMembership_withNullEffectiveFrom_defaultsToToday() {
        CreateMembershipRequest request = CreateMembershipRequest.builder()
                .userId(USER_ID)
                .propertyId(PROPERTY_ID)
                .role(Role.COMMITTEE_MEMBER)
                .effectiveFrom(null)
                .build();

        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(membershipRepository.existsActiveByUserIdAndPropertyIdAndRole(USER_ID, PROPERTY_ID, Role.COMMITTEE_MEMBER))
                .thenReturn(false);
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = membershipService.createMembership(request);

        assertThat(result.getEffectiveFrom()).isEqualTo(LocalDate.now());
    }

    // --- deactivateMembership tests ---

    @Test
    void deactivateMembership_withActiveMembership_deactivatesSuccessfully() {
        UUID membershipId = UUID.randomUUID();
        Membership membership = Membership.builder()
                .userId(USER_ID)
                .role(Role.PROPERTY_MANAGER)
                .isActive(true)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .build();
        membership.setPropertyId(PROPERTY_ID);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = membershipService.deactivateMembership(membershipId);

        assertThat(result.isActive()).isFalse();
        assertThat(result.getEffectiveTo()).isEqualTo(LocalDate.now());
    }

    @Test
    void deactivateMembership_preservesHistoricalData() {
        UUID membershipId = UUID.randomUUID();
        LocalDate originalEffectiveFrom = LocalDate.of(2024, 6, 1);
        Membership membership = Membership.builder()
                .userId(USER_ID)
                .unitId(UNIT_ID)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(originalEffectiveFrom)
                .build();
        membership.setPropertyId(PROPERTY_ID);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = membershipService.deactivateMembership(membershipId);

        // Historical data is preserved
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getPropertyId()).isEqualTo(PROPERTY_ID);
        assertThat(result.getUnitId()).isEqualTo(UNIT_ID);
        assertThat(result.getRole()).isEqualTo(Role.RESIDENT_OWNER);
        assertThat(result.getEffectiveFrom()).isEqualTo(originalEffectiveFrom);
        // Deactivation markers set
        assertThat(result.isActive()).isFalse();
        assertThat(result.getEffectiveTo()).isNotNull();
    }

    @Test
    void deactivateMembership_withNonExistentId_throwsException() {
        UUID membershipId = UUID.randomUUID();
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.deactivateMembership(membershipId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");
    }

    @Test
    void deactivateMembership_withAlreadyInactive_throwsException() {
        UUID membershipId = UUID.randomUUID();
        Membership membership = Membership.builder()
                .userId(USER_ID)
                .role(Role.TECHNICIAN)
                .isActive(false)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .effectiveTo(LocalDate.of(2025, 3, 1))
                .build();
        membership.setPropertyId(PROPERTY_ID);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> membershipService.deactivateMembership(membershipId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already inactive");

        verify(membershipRepository, never()).save(any());
    }

    // --- getActiveMemberships tests ---

    @Test
    void getActiveMemberships_returnsActiveRecords() {
        Membership m1 = Membership.builder().userId(USER_ID).role(Role.PROPERTY_MANAGER).isActive(true).build();
        Membership m2 = Membership.builder().userId(USER_ID).role(Role.COMMITTEE_MEMBER).isActive(true).build();
        m1.setPropertyId(PROPERTY_ID);
        m2.setPropertyId(PROPERTY_ID);

        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(m1, m2));

        List<Membership> result = membershipService.getActiveMemberships(USER_ID, PROPERTY_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Membership::getRole)
                .containsExactlyInAnyOrder(Role.PROPERTY_MANAGER, Role.COMMITTEE_MEMBER);
    }

    // --- hasActiveMembership tests ---

    @Test
    void hasActiveMembership_withActive_returnsTrue() {
        when(membershipRepository.hasActiveMembership(USER_ID, PROPERTY_ID)).thenReturn(true);

        assertThat(membershipService.hasActiveMembership(USER_ID, PROPERTY_ID)).isTrue();
    }

    @Test
    void hasActiveMembership_withNoActive_returnsFalse() {
        when(membershipRepository.hasActiveMembership(USER_ID, PROPERTY_ID)).thenReturn(false);

        assertThat(membershipService.hasActiveMembership(USER_ID, PROPERTY_ID)).isFalse();
    }

    // --- linkResidentToUnit tests ---

    @Test
    void linkResidentToUnit_withResidentOwner_linksSuccessfully() {
        UUID membershipId = UUID.randomUUID();
        UUID newUnitId = UUID.randomUUID();
        Membership membership = Membership.builder()
                .userId(USER_ID)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .build();
        membership.setPropertyId(PROPERTY_ID);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = membershipService.linkResidentToUnit(membershipId, newUnitId);

        assertThat(result.getUnitId()).isEqualTo(newUnitId);
    }

    @Test
    void linkResidentToUnit_withResidentTenant_linksSuccessfully() {
        UUID membershipId = UUID.randomUUID();
        UUID newUnitId = UUID.randomUUID();
        Membership membership = Membership.builder()
                .userId(USER_ID)
                .role(Role.RESIDENT_TENANT)
                .isActive(true)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .build();
        membership.setPropertyId(PROPERTY_ID);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = membershipService.linkResidentToUnit(membershipId, newUnitId);

        assertThat(result.getUnitId()).isEqualTo(newUnitId);
    }

    @Test
    void linkResidentToUnit_withNonResidentRole_throwsException() {
        UUID membershipId = UUID.randomUUID();
        Membership membership = Membership.builder()
                .userId(USER_ID)
                .role(Role.TECHNICIAN)
                .isActive(true)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .build();
        membership.setPropertyId(PROPERTY_ID);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> membershipService.linkResidentToUnit(membershipId, UNIT_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Only resident roles");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void linkResidentToUnit_withInactiveMembership_throwsException() {
        UUID membershipId = UUID.randomUUID();
        Membership membership = Membership.builder()
                .userId(USER_ID)
                .role(Role.RESIDENT_OWNER)
                .isActive(false)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .build();
        membership.setPropertyId(PROPERTY_ID);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> membershipService.linkResidentToUnit(membershipId, UNIT_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("inactive membership");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void linkResidentToUnit_withNonExistentMembership_throwsException() {
        UUID membershipId = UUID.randomUUID();
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.linkResidentToUnit(membershipId, UNIT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");
    }
}
