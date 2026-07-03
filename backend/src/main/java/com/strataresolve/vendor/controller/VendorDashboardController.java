package com.strataresolve.vendor.controller;

import com.strataresolve.vendor.dto.VendorTicketSummaryResponse;
import com.strataresolve.vendor.dto.VendorWorkOrderResponse;
import com.strataresolve.vendor.service.VendorDataScopeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for vendor-scoped data access.
 * Provides endpoints for vendor users (VENDOR_ADMIN and VENDOR_TECHNICIAN)
 * to access their assigned work orders and minimal ticket information.
 *
 * <p>Enforces vendor data scope: vendors can only see their own work orders
 * and minimal ticket info (reference number, title, category, status).
 * They cannot browse unrelated property data or other vendors' work orders.
 *
 * <p>Validates: Requirements 13.5, 18.4
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/vendor/my")
public class VendorDashboardController {

    private final VendorDataScopeService vendorDataScopeService;

    public VendorDashboardController(VendorDataScopeService vendorDataScopeService) {
        this.vendorDataScopeService = vendorDataScopeService;
    }

    /**
     * Lists all work orders assigned to the current vendor user's vendor,
     * with minimal ticket information.
     * Restricted to VENDOR_ADMIN and VENDOR_TECHNICIAN roles.
     */
    @GetMapping("/work-orders")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<List<VendorWorkOrderResponse>> getMyWorkOrders(
            @PathVariable UUID propertyId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        List<VendorWorkOrderResponse> workOrders =
                vendorDataScopeService.getWorkOrdersForVendorUser(userId, propertyId);
        return ResponseEntity.ok(workOrders);
    }

    /**
     * Gets a specific work order by ID, enforcing that it belongs to the
     * current vendor user's vendor. Returns the work order with minimal ticket info.
     * Restricted to VENDOR_ADMIN and VENDOR_TECHNICIAN roles.
     */
    @GetMapping("/work-orders/{workOrderId}")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<VendorWorkOrderResponse> getMyWorkOrder(
            @PathVariable UUID propertyId,
            @PathVariable UUID workOrderId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        VendorWorkOrderResponse workOrder =
                vendorDataScopeService.getWorkOrderForVendorUser(workOrderId, userId, propertyId);
        return ResponseEntity.ok(workOrder);
    }

    /**
     * Gets minimal ticket information for a ticket associated with a work order
     * assigned to the current vendor user's vendor.
     * Restricted to VENDOR_ADMIN and VENDOR_TECHNICIAN roles.
     *
     * <p>Returns only: reference number, title, category, and status.
     * Does NOT expose full ticket details like description, location, SLA data, or resident info.
     */
    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<VendorTicketSummaryResponse> getTicketSummary(
            @PathVariable UUID propertyId,
            @PathVariable UUID ticketId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        VendorTicketSummaryResponse summary =
                vendorDataScopeService.getTicketSummaryForVendorUser(ticketId, userId, propertyId);
        return ResponseEntity.ok(summary);
    }
}
