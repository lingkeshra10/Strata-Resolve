package com.strataresolve.vendor.controller;

import com.strataresolve.vendor.domain.Vendor;
import com.strataresolve.vendor.dto.CreateVendorRequest;
import com.strataresolve.vendor.dto.UpdateVendorRequest;
import com.strataresolve.vendor.dto.VendorResponse;
import com.strataresolve.vendor.service.VendorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for vendor management within a property.
 * All CRUD operations are restricted to Property Managers.
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/vendors")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    /**
     * Registers a new vendor and associates it with the specified property.
     * Restricted to Property Manager role.
     */
    @PostMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<VendorResponse> registerVendor(
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateVendorRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Vendor vendor = vendorService.register(propertyId, request, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(VendorResponse.from(vendor));
    }

    /**
     * Updates an existing vendor's details.
     * Restricted to Property Manager role.
     */
    @PutMapping("/{vendorId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<VendorResponse> updateVendor(
            @PathVariable UUID propertyId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody UpdateVendorRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Vendor vendor = vendorService.update(vendorId, propertyId, request, actingUserId);
        return ResponseEntity.ok(VendorResponse.from(vendor));
    }

    /**
     * Retrieves a vendor by ID within the property.
     * Restricted to Property Manager role.
     */
    @GetMapping("/{vendorId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<VendorResponse> getVendor(
            @PathVariable UUID propertyId,
            @PathVariable UUID vendorId) {
        Vendor vendor = vendorService.findById(vendorId, propertyId);
        return ResponseEntity.ok(VendorResponse.from(vendor));
    }

    /**
     * Lists vendors within a property.
     * Supports filtering by active status.
     * Restricted to Property Manager role.
     */
    @GetMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<List<VendorResponse>> listVendors(
            @PathVariable UUID propertyId,
            @RequestParam(required = false) Boolean activeOnly) {
        List<Vendor> vendors;
        if (Boolean.TRUE.equals(activeOnly)) {
            vendors = vendorService.findActiveByPropertyId(propertyId);
        } else {
            vendors = vendorService.findByPropertyId(propertyId);
        }
        List<VendorResponse> response = vendors.stream()
                .map(VendorResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Deactivates a vendor, preventing new work order assignments.
     * Restricted to Property Manager role.
     */
    @PostMapping("/{vendorId}/deactivate")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<VendorResponse> deactivateVendor(
            @PathVariable UUID propertyId,
            @PathVariable UUID vendorId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Vendor vendor = vendorService.deactivate(vendorId, propertyId, actingUserId);
        return ResponseEntity.ok(VendorResponse.from(vendor));
    }

    /**
     * Reactivates a vendor, allowing new work order assignments.
     * Restricted to Property Manager role.
     */
    @PostMapping("/{vendorId}/activate")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<VendorResponse> activateVendor(
            @PathVariable UUID propertyId,
            @PathVariable UUID vendorId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Vendor vendor = vendorService.activate(vendorId, propertyId, actingUserId);
        return ResponseEntity.ok(VendorResponse.from(vendor));
    }

    /**
     * Deletes a vendor from the property.
     * Restricted to Property Manager role.
     */
    @DeleteMapping("/{vendorId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<Void> deleteVendor(
            @PathVariable UUID propertyId,
            @PathVariable UUID vendorId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        vendorService.delete(vendorId, propertyId, actingUserId);
        return ResponseEntity.noContent().build();
    }
}
