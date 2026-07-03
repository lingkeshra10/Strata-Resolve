package com.strataresolve.property;

import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.domain.WorkOrderStatus;
import com.strataresolve.vendor.dto.VendorTicketSummaryResponse;
import com.strataresolve.vendor.dto.VendorWorkOrderResponse;
import com.strataresolve.vendor.repository.WorkOrderRepository;
import com.strataresolve.vendor.service.VendorDataScopeService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for Vendor Data Scope.
 *
 * <p><b>Property 22: Vendor Data Scope</b></p>
 * <p>For any vendor user, data access SHALL be limited to work orders assigned to their vendor
 * and minimal ticket information for those work orders only. The vendor SHALL NOT be able to
 * browse unrelated property data, other vendors' work orders, or full ticket details beyond
 * their assignments.</p>
 *
 * <p><b>Validates: Requirements 13.5, 18.4</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 22: Vendor Data Scope")
class VendorDataScopePropertyTest {

    // =====================================================================
    // Property: Vendor users only see work orders assigned to their vendor
    // =====================================================================

    /**
     * For any vendor user with a valid vendor membership, the work orders returned
     * SHALL all belong to that vendor (vendorId matches). No work orders from other
     * vendors shall be included in the results.
     *
     * <p><b>Validates: Requirements 13.5, 18.4</b></p>
     */
    @Property(tries = 100)
    void vendorUserOnlySeesOwnVendorWorkOrders(
            @ForAll("vendorScenario") VendorScenario scenario
    ) {
        // Arrange
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        VendorDataScopeService service = new VendorDataScopeService(
                workOrderRepository, ticketRepository, membershipRepository);

        when(membershipRepository.findActiveByUserIdAndPropertyId(scenario.userId(), scenario.propertyId()))
                .thenReturn(List.of(scenario.membership()));
        when(workOrderRepository.findByVendorIdAndPropertyId(scenario.vendorId(), scenario.propertyId()))
                .thenReturn(scenario.ownWorkOrders());

        // Stub ticket lookups for each work order
        for (WorkOrder wo : scenario.ownWorkOrders()) {
            Ticket ticket = buildTicket(wo.getTicketId(), scenario.propertyId());
            when(ticketRepository.findById(wo.getTicketId())).thenReturn(Optional.of(ticket));
        }

        // Act
        List<VendorWorkOrderResponse> result = service.getWorkOrdersForVendorUser(
                scenario.userId(), scenario.propertyId());

        // Assert: all returned work orders belong to the vendor
        assertThat(result).allSatisfy(response ->
                assertThat(response.vendorId())
                        .as("Work order %s must belong to vendor %s", response.id(), scenario.vendorId())
                        .isEqualTo(scenario.vendorId()));

        // Assert: count matches what was assigned to vendor
        assertThat(result).hasSameSizeAs(scenario.ownWorkOrders());
    }

    // =====================================================================
    // Property: Vendor cannot access another vendor's work order
    // =====================================================================

    /**
     * For any vendor user attempting to access a work order belonging to a different vendor,
     * the system SHALL reject the access with an AccessDeniedException.
     *
     * <p><b>Validates: Requirements 13.5, 18.4</b></p>
     */
    @Property(tries = 100)
    void vendorCannotAccessOtherVendorsWorkOrder(
            @ForAll("vendorScenario") VendorScenario scenario
    ) {
        // Arrange
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        VendorDataScopeService service = new VendorDataScopeService(
                workOrderRepository, ticketRepository, membershipRepository);

        when(membershipRepository.findActiveByUserIdAndPropertyId(scenario.userId(), scenario.propertyId()))
                .thenReturn(List.of(scenario.membership()));

        // Create a work order that belongs to another vendor
        UUID otherVendorId = UUID.randomUUID();
        UUID otherWorkOrderId = UUID.randomUUID();
        WorkOrder otherVendorWorkOrder = buildWorkOrder(otherWorkOrderId, otherVendorId,
                UUID.randomUUID(), scenario.propertyId());
        when(workOrderRepository.findByIdAndPropertyId(otherWorkOrderId, scenario.propertyId()))
                .thenReturn(Optional.of(otherVendorWorkOrder));

        // Act & Assert: access to other vendor's work order is rejected
        assertThatThrownBy(() -> service.getWorkOrderForVendorUser(
                otherWorkOrderId, scenario.userId(), scenario.propertyId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =====================================================================
    // Property: Vendor ticket summary only exposes minimal fields
    // =====================================================================

    /**
     * For any vendor user accessing ticket information through their work orders,
     * the response SHALL only contain minimal fields: referenceNumber, title, category, status.
     * Full ticket details (description, location, SLA data) SHALL NOT be exposed.
     *
     * <p><b>Validates: Requirements 13.5, 18.4</b></p>
     */
    @Property(tries = 100)
    void vendorTicketSummaryOnlyExposesMinimalFields(
            @ForAll("vendorWithWorkOrder") VendorWithWorkOrderScenario scenario
    ) {
        // Arrange
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        VendorDataScopeService service = new VendorDataScopeService(
                workOrderRepository, ticketRepository, membershipRepository);

        when(membershipRepository.findActiveByUserIdAndPropertyId(scenario.userId(), scenario.propertyId()))
                .thenReturn(List.of(scenario.membership()));
        when(workOrderRepository.findByTicketIdAndPropertyId(scenario.ticketId(), scenario.propertyId()))
                .thenReturn(Optional.of(scenario.workOrder()));
        when(ticketRepository.findById(scenario.ticketId()))
                .thenReturn(Optional.of(scenario.ticket()));

        // Act
        VendorTicketSummaryResponse result = service.getTicketSummaryForVendorUser(
                scenario.ticketId(), scenario.userId(), scenario.propertyId());

        // Assert: only minimal fields are present
        assertThat(result.referenceNumber()).isEqualTo(scenario.ticket().getReferenceNumber());
        assertThat(result.title()).isEqualTo(scenario.ticket().getTitle());
        assertThat(result.category()).isEqualTo(scenario.ticket().getCategory());
        assertThat(result.status()).isEqualTo(scenario.ticket().getStatus());

        // The VendorTicketSummaryResponse record only has 4 fields by design -
        // verify the DTO structure does not leak sensitive ticket data
        assertThat(VendorTicketSummaryResponse.class.getRecordComponents()).hasSize(4);
    }

    // =====================================================================
    // Property: Non-vendor users are denied access
    // =====================================================================

    /**
     * For any user who does not have a VENDOR_ADMIN or VENDOR_TECHNICIAN role,
     * attempting to access vendor-scoped data SHALL result in an AccessDeniedException.
     *
     * <p><b>Validates: Requirements 13.5, 18.4</b></p>
     */
    @Property(tries = 100)
    void nonVendorUsersAreDeniedAccess(
            @ForAll("nonVendorRole") Role nonVendorRole
    ) {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        VendorDataScopeService service = new VendorDataScopeService(
                workOrderRepository, ticketRepository, membershipRepository);

        Membership nonVendorMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .role(nonVendorRole)
                .vendorId(null)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        nonVendorMembership.setPropertyId(propertyId);

        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(nonVendorMembership));

        // Act & Assert: all vendor-scoped operations are denied
        assertThatThrownBy(() -> service.getWorkOrdersForVendorUser(userId, propertyId))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> service.getWorkOrderForVendorUser(UUID.randomUUID(), userId, propertyId))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> service.getTicketSummaryForVendorUser(UUID.randomUUID(), userId, propertyId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =====================================================================
    // Property: Vendor cannot access tickets without an assigned work order
    // =====================================================================

    /**
     * For any vendor user attempting to access a ticket that does NOT have a work order
     * assigned to their vendor, the system SHALL reject the access.
     *
     * <p><b>Validates: Requirements 13.5, 18.4</b></p>
     */
    @Property(tries = 100)
    void vendorCannotAccessTicketWithoutAssignedWorkOrder(
            @ForAll("vendorScenario") VendorScenario scenario
    ) {
        // Arrange
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        VendorDataScopeService service = new VendorDataScopeService(
                workOrderRepository, ticketRepository, membershipRepository);

        when(membershipRepository.findActiveByUserIdAndPropertyId(scenario.userId(), scenario.propertyId()))
                .thenReturn(List.of(scenario.membership()));

        // Ticket has no work order for this vendor (no work order exists for this ticket at all)
        UUID unrelatedTicketId = UUID.randomUUID();
        when(workOrderRepository.findByTicketIdAndPropertyId(unrelatedTicketId, scenario.propertyId()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getTicketSummaryForVendorUser(
                unrelatedTicketId, scenario.userId(), scenario.propertyId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    @Provide
    Arbitrary<VendorScenario> vendorScenario() {
        Arbitrary<Integer> workOrderCount = Arbitraries.integers().between(0, 8);
        Arbitrary<Role> vendorRole = Arbitraries.of(Role.VENDOR_ADMIN, Role.VENDOR_TECHNICIAN);

        return Combinators.combine(workOrderCount, vendorRole)
                .as((woCount, role) -> {
                    UUID propertyId = UUID.randomUUID();
                    UUID userId = UUID.randomUUID();
                    UUID vendorId = UUID.randomUUID();

                    Membership membership = Membership.builder()
                            .id(UUID.randomUUID())
                            .userId(userId)
                            .role(role)
                            .vendorId(vendorId)
                            .isActive(true)
                            .effectiveFrom(LocalDate.now().minusDays(30))
                            .build();
                    membership.setPropertyId(propertyId);

                    List<WorkOrder> ownWorkOrders = IntStream.range(0, woCount)
                            .mapToObj(i -> buildWorkOrder(
                                    UUID.randomUUID(), vendorId, UUID.randomUUID(), propertyId))
                            .toList();

                    return new VendorScenario(propertyId, userId, vendorId, membership, ownWorkOrders);
                });
    }

    @Provide
    Arbitrary<VendorWithWorkOrderScenario> vendorWithWorkOrder() {
        Arbitrary<Role> vendorRole = Arbitraries.of(Role.VENDOR_ADMIN, Role.VENDOR_TECHNICIAN);
        Arbitrary<Category> category = Arbitraries.of(Category.values());
        Arbitrary<TicketStatus> status = Arbitraries.of(TicketStatus.values());

        return Combinators.combine(vendorRole, category, status)
                .as((role, cat, ticketStatus) -> {
                    UUID propertyId = UUID.randomUUID();
                    UUID userId = UUID.randomUUID();
                    UUID vendorId = UUID.randomUUID();
                    UUID ticketId = UUID.randomUUID();
                    UUID workOrderId = UUID.randomUUID();

                    Membership membership = Membership.builder()
                            .id(UUID.randomUUID())
                            .userId(userId)
                            .role(role)
                            .vendorId(vendorId)
                            .isActive(true)
                            .effectiveFrom(LocalDate.now().minusDays(30))
                            .build();
                    membership.setPropertyId(propertyId);

                    WorkOrder workOrder = buildWorkOrder(workOrderId, vendorId, ticketId, propertyId);

                    Ticket ticket = Ticket.builder()
                            .id(ticketId)
                            .submittedBy(UUID.randomUUID())
                            .unitId(UUID.randomUUID())
                            .referenceNumber("SR-2025-" + String.format("%06d",
                                    Math.abs(ticketId.hashCode() % 999999) + 1))
                            .title("Ticket " + ticketId.toString().substring(0, 8))
                            .description("Detailed description that vendors should NOT see")
                            .category(cat)
                            .priority(com.strataresolve.ticket.domain.Priority.NORMAL)
                            .status(ticketStatus)
                            .location("Block B, Level 5 - should NOT be visible to vendor")
                            .build();
                    ticket.setPropertyId(propertyId);

                    return new VendorWithWorkOrderScenario(
                            propertyId, userId, vendorId, ticketId, membership, workOrder, ticket);
                });
    }

    @Provide
    Arbitrary<Role> nonVendorRole() {
        return Arbitraries.of(
                Role.PLATFORM_ADMIN,
                Role.PROPERTY_MANAGER,
                Role.COMMITTEE_MEMBER,
                Role.RESIDENT_OWNER,
                Role.RESIDENT_TENANT,
                Role.TECHNICIAN
        );
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private static WorkOrder buildWorkOrder(UUID id, UUID vendorId, UUID ticketId, UUID propertyId) {
        WorkOrder wo = WorkOrder.builder()
                .ticketId(ticketId)
                .vendorId(vendorId)
                .status(WorkOrderStatus.CREATED)
                .createdAt(Instant.now())
                .build();
        wo.setId(id);
        wo.setPropertyId(propertyId);
        return wo;
    }

    private static Ticket buildTicket(UUID ticketId, UUID propertyId) {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-" + String.format("%06d",
                        Math.abs(ticketId.hashCode() % 999999) + 1))
                .title("Test ticket " + ticketId.toString().substring(0, 8))
                .description("Detailed internal description - not for vendors")
                .category(Category.PLUMBING)
                .priority(com.strataresolve.ticket.domain.Priority.NORMAL)
                .status(TicketStatus.ASSIGNED)
                .location("Block A, Level 2")
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }

    // =====================================================================
    // Test Data Records
    // =====================================================================

    record VendorScenario(
            UUID propertyId,
            UUID userId,
            UUID vendorId,
            Membership membership,
            List<WorkOrder> ownWorkOrders
    ) {}

    record VendorWithWorkOrderScenario(
            UUID propertyId,
            UUID userId,
            UUID vendorId,
            UUID ticketId,
            Membership membership,
            WorkOrder workOrder,
            Ticket ticket
    ) {}
}
