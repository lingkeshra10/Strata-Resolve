package com.strataresolve.sla.controller;

import com.strataresolve.sla.domain.SlaPolicy;
import com.strataresolve.sla.dto.CreateSlaPolicyRequest;
import com.strataresolve.sla.dto.SlaPolicyResponse;
import com.strataresolve.sla.dto.UpdateSlaPolicyRequest;
import com.strataresolve.sla.service.SlaPolicyService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for SLA policy management.
 * All operations are restricted to Property Managers.
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/sla-policies")
public class SlaPolicyController {

    private final SlaPolicyService slaPolicyService;

    public SlaPolicyController(SlaPolicyService slaPolicyService) {
        this.slaPolicyService = slaPolicyService;
    }

    /**
     * Creates a new SLA policy for the specified property.
     */
    @PostMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<SlaPolicyResponse> createPolicy(
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateSlaPolicyRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        SlaPolicy policy = slaPolicyService.create(propertyId, request, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(SlaPolicyResponse.from(policy));
    }

    /**
     * Updates an existing SLA policy.
     */
    @PutMapping("/{policyId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<SlaPolicyResponse> updatePolicy(
            @PathVariable UUID propertyId,
            @PathVariable UUID policyId,
            @Valid @RequestBody UpdateSlaPolicyRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        SlaPolicy policy = slaPolicyService.update(policyId, propertyId, request, actingUserId);
        return ResponseEntity.ok(SlaPolicyResponse.from(policy));
    }

    /**
     * Retrieves an SLA policy by ID.
     */
    @GetMapping("/{policyId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<SlaPolicyResponse> getPolicy(
            @PathVariable UUID propertyId,
            @PathVariable UUID policyId) {
        SlaPolicy policy = slaPolicyService.findById(policyId);
        return ResponseEntity.ok(SlaPolicyResponse.from(policy));
    }

    /**
     * Lists all SLA policies for a property.
     */
    @GetMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<List<SlaPolicyResponse>> listPolicies(@PathVariable UUID propertyId) {
        List<SlaPolicyResponse> policies = slaPolicyService.findByPropertyId(propertyId).stream()
                .map(SlaPolicyResponse::from)
                .toList();
        return ResponseEntity.ok(policies);
    }

    /**
     * Deletes an SLA policy.
     */
    @DeleteMapping("/{policyId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<Void> deletePolicy(
            @PathVariable UUID propertyId,
            @PathVariable UUID policyId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        slaPolicyService.delete(policyId, propertyId, actingUserId);
        return ResponseEntity.noContent().build();
    }
}
