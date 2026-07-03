package com.strataresolve.vendor.service;

import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorDataScopeServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private VendorDataScopeService vendorDataScopeService;

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID VENDOR_ID = UUID.randomUUID();
    private static final UUID OTHER_VENDOR_ID = UUID.randomUUID();
    private static final UUID TICKET_ID = UUID.randomUUID();
    private static final UUID WORK_ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        vendorDataScopeService = new VendorDataScopeService(
                workOrderRepository, ticketRepository, membershipRepository);
    }

    // --- resolveVendorIdForUser tests ---

    @Test
    void resolveVendorIdForUser_withVendorTechnician_returnsVendorId() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        UUID result = vendorDataScopeService.resolveVendorIdForUser(USER_ID, PROPERTY_ID);

        assertThat(result).isEqualTo(VENDOR_ID);
    }

    @Test
    void resolveVendorIdForUser_withVendorAdmin_returnsVendorId() {
        Membership membership = createVendorMembership(Role.VENDOR_ADMIN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        UUID result = vendorDataScopeService.resolveVendorIdForUser(USER_ID, PROPERTY_ID);

        assertThat(result).isEqualTo(VENDOR_ID);
    }

    @Test
    void resolveVendorIdForUser_withNonVendorRole_throwsAccessDenied() {
        Membership membership = createMembership(Role.RESIDENT_OWNER, null);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        assertThatThrownBy(() -> vendorDataScopeService.resolveVendorIdForUser(USER_ID, PROPERTY_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not associated with a vendor");
    }

    @Test
    void resolveVendorIdForUser_withNoMemberships_throwsAccessDenied() {
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> vendorDataScopeService.resolveVendorIdForUser(USER_ID, PROPERTY_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not associated with a vendor");
    }

    @Test
    void resolveVendorIdForUser_withVendorRoleButNoVendorId_throwsAccessDenied() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, null);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        assertThatThrownBy(() -> vendorDataScopeService.resolveVendorIdForUser(USER_ID, PROPERTY_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not associated with a vendor");
    }

    // --- getWorkOrdersForVendorUser tests ---

    @Test
    void getWorkOrdersForVendorUser_returnsOnlyAssignedWorkOrders() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        WorkOrder wo1 = createWorkOrder(WORK_ORDER_ID, VENDOR_ID, TICKET_ID);
        UUID ticketId2 = UUID.randomUUID();
        UUID workOrderId2 = UUID.randomUUID();
        WorkOrder wo2 = createWorkOrder(workOrderId2, VENDOR_ID, ticketId2);
        when(workOrderRepository.findByVendorIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(List.of(wo1, wo2));

        Ticket ticket1 = createTicket(TICKET_ID, "SR-2025-000001", "Broken pipe", Category.PLUMBING, TicketStatus.ASSIGNED);
        Ticket ticket2 = createTicket(ticketId2, "SR-2025-000002", "Light flickering", Category.ELECTRICAL, TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket1));
        when(ticketRepository.findById(ticketId2)).thenReturn(Optional.of(ticket2));

        List<VendorWorkOrderResponse> result =
                vendorDataScopeService.getWorkOrdersForVendorUser(USER_ID, PROPERTY_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).vendorId()).isEqualTo(VENDOR_ID);
        assertThat(result.get(0).ticket().referenceNumber()).isEqualTo("SR-2025-000001");
        assertThat(result.get(0).ticket().title()).isEqualTo("Broken pipe");
        assertThat(result.get(0).ticket().category()).isEqualTo(Category.PLUMBING);
        assertThat(result.get(0).ticket().status()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(result.get(1).ticket().referenceNumber()).isEqualTo("SR-2025-000002");
    }

    @Test
    void getWorkOrdersForVendorUser_withNoWorkOrders_returnsEmptyList() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));
        when(workOrderRepository.findByVendorIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Collections.emptyList());

        List<VendorWorkOrderResponse> result =
                vendorDataScopeService.getWorkOrdersForVendorUser(USER_ID, PROPERTY_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getWorkOrdersForVendorUser_withNonVendorUser_throwsAccessDenied() {
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> vendorDataScopeService.getWorkOrdersForVendorUser(USER_ID, PROPERTY_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- getWorkOrderForVendorUser tests ---

    @Test
    void getWorkOrderForVendorUser_withOwnWorkOrder_returnsResponse() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        WorkOrder workOrder = createWorkOrder(WORK_ORDER_ID, VENDOR_ID, TICKET_ID);
        when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                .thenReturn(Optional.of(workOrder));

        Ticket ticket = createTicket(TICKET_ID, "SR-2025-000001", "Broken pipe", Category.PLUMBING, TicketStatus.ASSIGNED);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        VendorWorkOrderResponse result =
                vendorDataScopeService.getWorkOrderForVendorUser(WORK_ORDER_ID, USER_ID, PROPERTY_ID);

        assertThat(result.id()).isEqualTo(WORK_ORDER_ID);
        assertThat(result.vendorId()).isEqualTo(VENDOR_ID);
        assertThat(result.status()).isEqualTo(WorkOrderStatus.CREATED);
        assertThat(result.ticket().referenceNumber()).isEqualTo("SR-2025-000001");
        assertThat(result.ticket().title()).isEqualTo("Broken pipe");
    }

    @Test
    void getWorkOrderForVendorUser_withOtherVendorsWorkOrder_throwsAccessDenied() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        WorkOrder workOrder = createWorkOrder(WORK_ORDER_ID, OTHER_VENDOR_ID, TICKET_ID);
        when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> vendorDataScopeService.getWorkOrderForVendorUser(WORK_ORDER_ID, USER_ID, PROPERTY_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("do not have permission");
    }

    @Test
    void getWorkOrderForVendorUser_withNonExistentWorkOrder_throwsNotFound() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));
        when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorDataScopeService.getWorkOrderForVendorUser(WORK_ORDER_ID, USER_ID, PROPERTY_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("WorkOrder");
    }

    // --- getTicketSummaryForVendorUser tests ---

    @Test
    void getTicketSummaryForVendorUser_withAssignedTicket_returnsMinimalInfo() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        WorkOrder workOrder = createWorkOrder(WORK_ORDER_ID, VENDOR_ID, TICKET_ID);
        when(workOrderRepository.findByTicketIdAndPropertyId(TICKET_ID, PROPERTY_ID))
                .thenReturn(Optional.of(workOrder));

        Ticket ticket = createTicket(TICKET_ID, "SR-2025-000001", "Broken pipe", Category.PLUMBING, TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        VendorTicketSummaryResponse result =
                vendorDataScopeService.getTicketSummaryForVendorUser(TICKET_ID, USER_ID, PROPERTY_ID);

        assertThat(result.referenceNumber()).isEqualTo("SR-2025-000001");
        assertThat(result.title()).isEqualTo("Broken pipe");
        assertThat(result.category()).isEqualTo(Category.PLUMBING);
        assertThat(result.status()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void getTicketSummaryForVendorUser_withUnassignedTicket_throwsAccessDenied() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        when(workOrderRepository.findByTicketIdAndPropertyId(TICKET_ID, PROPERTY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorDataScopeService.getTicketSummaryForVendorUser(TICKET_ID, USER_ID, PROPERTY_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("do not have permission");
    }

    @Test
    void getTicketSummaryForVendorUser_withOtherVendorsTicket_throwsAccessDenied() {
        Membership membership = createVendorMembership(Role.VENDOR_TECHNICIAN, VENDOR_ID);
        when(membershipRepository.findActiveByUserIdAndPropertyId(USER_ID, PROPERTY_ID))
                .thenReturn(List.of(membership));

        WorkOrder workOrder = createWorkOrder(WORK_ORDER_ID, OTHER_VENDOR_ID, TICKET_ID);
        when(workOrderRepository.findByTicketIdAndPropertyId(TICKET_ID, PROPERTY_ID))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> vendorDataScopeService.getTicketSummaryForVendorUser(TICKET_ID, USER_ID, PROPERTY_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("do not have permission");
    }

    // --- helper methods ---

    private Membership createVendorMembership(Role role, UUID vendorId) {
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .role(role)
                .vendorId(vendorId)
                .isActive(true)
                .effectiveFrom(LocalDate.now().minusDays(30))
                .build();
    }

    private Membership createMembership(Role role, UUID vendorId) {
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .role(role)
                .vendorId(vendorId)
                .isActive(true)
                .effectiveFrom(LocalDate.now().minusDays(30))
                .build();
    }

    private WorkOrder createWorkOrder(UUID id, UUID vendorId, UUID ticketId) {
        WorkOrder wo = WorkOrder.builder()
                .ticketId(ticketId)
                .vendorId(vendorId)
                .status(WorkOrderStatus.CREATED)
                .createdAt(Instant.now())
                .build();
        wo.setId(id);
        wo.setPropertyId(PROPERTY_ID);
        return wo;
    }

    private Ticket createTicket(UUID id, String refNumber, String title, Category category, TicketStatus status) {
        Ticket ticket = Ticket.builder()
                .referenceNumber(refNumber)
                .title(title)
                .description("Some detailed description that vendors should not see")
                .category(category)
                .status(status)
                .location("Block A, Level 3")
                .build();
        ticket.setId(id);
        ticket.setPropertyId(PROPERTY_ID);
        return ticket;
    }
}
