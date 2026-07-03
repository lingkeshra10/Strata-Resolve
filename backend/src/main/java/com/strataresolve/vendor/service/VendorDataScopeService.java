package com.strataresolve.vendor.service;

import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.dto.VendorTicketSummaryResponse;
import com.strataresolve.vendor.dto.VendorWorkOrderResponse;
import com.strataresolve.vendor.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service enforcing vendor data scope restrictions.
 *
 * <p>Vendor users (VENDOR_ADMIN and VENDOR_TECHNICIAN) are restricted to:
 * <ul>
 *   <li>Only work orders assigned to their vendor</li>
 *   <li>Minimal ticket information (reference number, title, category, status) for assigned work orders</li>
 * </ul>
 *
 * <p>Vendor users CANNOT:
 * <ul>
 *   <li>Browse unrelated property data</li>
 *   <li>Access other vendors' work orders</li>
 *   <li>View full ticket details (description, location, SLA data, resident info)</li>
 * </ul>
 *
 * <p>Validates: Requirements 13.5, 18.4
 */
@Service
@Transactional(readOnly = true)
public class VendorDataScopeService {

    private final WorkOrderRepository workOrderRepository;
    private final TicketRepository ticketRepository;
    private final MembershipRepository membershipRepository;

    public VendorDataScopeService(WorkOrderRepository workOrderRepository,
                                  TicketRepository ticketRepository,
                                  MembershipRepository membershipRepository) {
        this.workOrderRepository = workOrderRepository;
        this.ticketRepository = ticketRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Retrieves all work orders assigned to the vendor that the given user belongs to,
     * along with minimal ticket information for each.
     *
     * @param userId the vendor user requesting data
     * @param propertyId the property context
     * @return list of work orders with minimal ticket summaries
     * @throws AccessDeniedException if the user is not a vendor user for the property
     */
    public List<VendorWorkOrderResponse> getWorkOrdersForVendorUser(UUID userId, UUID propertyId) {
        UUID vendorId = resolveVendorIdForUser(userId, propertyId);

        List<WorkOrder> workOrders = workOrderRepository.findByVendorIdAndPropertyId(vendorId, propertyId);

        return workOrders.stream()
                .map(wo -> {
                    VendorTicketSummaryResponse ticketSummary = getMinimalTicketInfo(wo.getTicketId());
                    return VendorWorkOrderResponse.from(wo, ticketSummary);
                })
                .toList();
    }

    /**
     * Retrieves a specific work order by ID, enforcing that it belongs to the vendor
     * associated with the requesting user.
     *
     * @param workOrderId the work order ID
     * @param userId the vendor user requesting access
     * @param propertyId the property context
     * @return the work order with minimal ticket summary
     * @throws AccessDeniedException if the user is not a vendor user or the work order belongs to another vendor
     * @throws ResourceNotFoundException if the work order does not exist
     */
    public VendorWorkOrderResponse getWorkOrderForVendorUser(UUID workOrderId, UUID userId, UUID propertyId) {
        UUID vendorId = resolveVendorIdForUser(userId, propertyId);

        WorkOrder workOrder = workOrderRepository.findByIdAndPropertyId(workOrderId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", workOrderId));

        if (!workOrder.getVendorId().equals(vendorId)) {
            throw new AccessDeniedException(
                    "You do not have permission to access this work order");
        }

        VendorTicketSummaryResponse ticketSummary = getMinimalTicketInfo(workOrder.getTicketId());
        return VendorWorkOrderResponse.from(workOrder, ticketSummary);
    }

    /**
     * Retrieves minimal ticket information for a ticket associated with a work order
     * assigned to the vendor of the requesting user.
     *
     * @param ticketId the ticket ID to get summary for
     * @param userId the vendor user requesting data
     * @param propertyId the property context
     * @return minimal ticket summary
     * @throws AccessDeniedException if the vendor does not have a work order for this ticket
     */
    public VendorTicketSummaryResponse getTicketSummaryForVendorUser(UUID ticketId, UUID userId, UUID propertyId) {
        UUID vendorId = resolveVendorIdForUser(userId, propertyId);

        // Verify this vendor has a work order for this ticket
        WorkOrder workOrder = workOrderRepository.findByTicketIdAndPropertyId(ticketId, propertyId)
                .orElseThrow(() -> new AccessDeniedException(
                        "You do not have permission to access this ticket"));

        if (!workOrder.getVendorId().equals(vendorId)) {
            throw new AccessDeniedException(
                    "You do not have permission to access this ticket");
        }

        return getMinimalTicketInfo(ticketId);
    }

    /**
     * Resolves the vendor ID that a user belongs to within a property.
     * The user must have an active membership with VENDOR_ADMIN or VENDOR_TECHNICIAN role
     * and a linked vendorId.
     *
     * @param userId the user ID
     * @param propertyId the property context
     * @return the vendor ID the user is linked to
     * @throws AccessDeniedException if the user is not a vendor user or has no vendor linkage
     */
    public UUID resolveVendorIdForUser(UUID userId, UUID propertyId) {
        List<Membership> memberships = membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId);

        return memberships.stream()
                .filter(m -> m.getRole() == Role.VENDOR_ADMIN || m.getRole() == Role.VENDOR_TECHNICIAN)
                .filter(m -> m.getVendorId() != null)
                .map(Membership::getVendorId)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException(
                        "User is not associated with a vendor for this property"));
    }

    private VendorTicketSummaryResponse getMinimalTicketInfo(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElse(null);

        if (ticket == null) {
            // Return a placeholder if ticket not found (shouldn't happen in normal flow)
            return new VendorTicketSummaryResponse(null, null, null, null);
        }

        return VendorTicketSummaryResponse.from(ticket);
    }
}
