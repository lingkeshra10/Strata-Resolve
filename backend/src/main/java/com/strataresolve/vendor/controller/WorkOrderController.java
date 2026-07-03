package com.strataresolve.vendor.controller;

import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.dto.AttachmentResponse;
import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.dto.CreateWorkOrderRequest;
import com.strataresolve.vendor.dto.WorkOrderResponse;
import com.strataresolve.vendor.dto.WorkOrderTransitionRequest;
import com.strataresolve.vendor.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for work order management.
 * Supports creation (Property Manager), lifecycle transitions, evidence uploads (Vendor Technician),
 * and querying work orders.
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    /**
     * Creates a new work order for a ticket assigned to a vendor.
     * Restricted to Property Manager role.
     */
    @PostMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<WorkOrderResponse> createWorkOrder(
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateWorkOrderRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        WorkOrder workOrder = workOrderService.create(
                request.ticketId(), request.vendorId(), propertyId, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkOrderResponse.from(workOrder));
    }

    /**
     * Retrieves a specific work order by ID.
     */
    @GetMapping("/{workOrderId}")
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<WorkOrderResponse> getWorkOrder(
            @PathVariable UUID propertyId,
            @PathVariable UUID workOrderId) {
        WorkOrder workOrder = workOrderService.findById(workOrderId, propertyId);
        return ResponseEntity.ok(WorkOrderResponse.from(workOrder));
    }

    /**
     * Lists all work orders for a property (Property Manager view).
     */
    @GetMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<List<WorkOrderResponse>> listWorkOrders(@PathVariable UUID propertyId) {
        List<WorkOrder> workOrders = workOrderService.findByPropertyId(propertyId);
        List<WorkOrderResponse> response = workOrders.stream()
                .map(WorkOrderResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Lists work orders visible to the current vendor technician.
     * Implements Requirement 19.3: All active Vendor_Technicians of the assigned vendor can see work orders.
     */
    @GetMapping("/my-work-orders")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<List<WorkOrderResponse>> listMyWorkOrders(
            @PathVariable UUID propertyId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        List<WorkOrder> workOrders = workOrderService.findByVendorTechnician(actingUserId, propertyId);
        List<WorkOrderResponse> response = workOrders.stream()
                .map(WorkOrderResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Lists work orders for a specific vendor.
     */
    @GetMapping("/vendor/{vendorId}")
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<List<WorkOrderResponse>> listVendorWorkOrders(
            @PathVariable UUID propertyId,
            @PathVariable UUID vendorId) {
        List<WorkOrder> workOrders = workOrderService.findByVendor(vendorId, propertyId);
        List<WorkOrderResponse> response = workOrders.stream()
                .map(WorkOrderResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Transitions a work order to a new status.
     * Supports accept, start work, complete, and cancel transitions.
     */
    @PostMapping("/{workOrderId}/transition")
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<WorkOrderResponse> transitionWorkOrder(
            @PathVariable UUID propertyId,
            @PathVariable UUID workOrderId,
            @Valid @RequestBody WorkOrderTransitionRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        WorkOrder workOrder = workOrderService.transition(
                workOrderId, propertyId, request.targetStatus(), actingUserId);
        return ResponseEntity.ok(WorkOrderResponse.from(workOrder));
    }

    /**
     * Accepts a work order (convenience endpoint).
     */
    @PostMapping("/{workOrderId}/accept")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<WorkOrderResponse> acceptWorkOrder(
            @PathVariable UUID propertyId,
            @PathVariable UUID workOrderId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        WorkOrder workOrder = workOrderService.accept(workOrderId, propertyId, actingUserId);
        return ResponseEntity.ok(WorkOrderResponse.from(workOrder));
    }

    /**
     * Marks a work order as completed (convenience endpoint).
     */
    @PostMapping("/{workOrderId}/complete")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<WorkOrderResponse> completeWorkOrder(
            @PathVariable UUID propertyId,
            @PathVariable UUID workOrderId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        WorkOrder workOrder = workOrderService.complete(workOrderId, propertyId, actingUserId);
        return ResponseEntity.ok(WorkOrderResponse.from(workOrder));
    }

    /**
     * Cancels a work order (convenience endpoint).
     */
    @PostMapping("/{workOrderId}/cancel")
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<WorkOrderResponse> cancelWorkOrder(
            @PathVariable UUID propertyId,
            @PathVariable UUID workOrderId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        WorkOrder workOrder = workOrderService.cancel(workOrderId, propertyId, actingUserId);
        return ResponseEntity.ok(WorkOrderResponse.from(workOrder));
    }

    /**
     * Uploads a quotation or completion evidence to a work order.
     * Stores the attachment and notifies the Property_Manager.
     * Implements Requirement 19.5.
     */
    @PostMapping(value = "/{workOrderId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_TECHNICIAN')")
    public ResponseEntity<AttachmentResponse> uploadEvidence(
            @PathVariable UUID propertyId,
            @PathVariable UUID workOrderId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "COMPLETION_EVIDENCE") String attachmentType,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Attachment attachment = workOrderService.uploadEvidence(
                workOrderId, propertyId, file, attachmentType, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(AttachmentResponse.from(attachment));
    }
}
