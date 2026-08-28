package com.example.property.controller;

import com.example.property.domain.Property;
import com.example.property.dto.CreatePropertyRequest;
import com.example.property.dto.PropertyResponse;
import com.example.property.dto.UpdatePropertyRequest;
import com.example.property.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for property management.
 * All operations are restricted to Platform Administrators.
 * Properties are top-level entities — no tenant filter applies.
 */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /**
     * Creates a new property.
     * Restricted to Platform Admin role.
     */
    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PropertyResponse> createProperty(
            @Valid @RequestBody CreatePropertyRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Property property = propertyService.create(request, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(PropertyResponse.from(property));
    }

    /**
     * Updates an existing property.
     * Restricted to Platform Admin role.
     */
    @PutMapping("/{propertyId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PropertyResponse> updateProperty(
            @PathVariable UUID propertyId,
            @Valid @RequestBody UpdatePropertyRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Property property = propertyService.update(propertyId, request, actingUserId);
        return ResponseEntity.ok(PropertyResponse.from(property));
    }

    /**
     * Retrieves a property by ID.
     * Restricted to Platform Admin role.
     */
    @GetMapping("/{propertyId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PropertyResponse> getProperty(@PathVariable UUID propertyId) {
        Property property = propertyService.findById(propertyId);
        return ResponseEntity.ok(PropertyResponse.from(property));
    }

    /**
     * Lists all properties.
     * Restricted to Platform Admin role.
     */
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PropertyResponse>> listProperties() {
        List<PropertyResponse> properties = propertyService.findAll().stream()
                .map(PropertyResponse::from)
                .toList();
        return ResponseEntity.ok(properties);
    }

    /**
     * Activates a property, allowing new ticket submissions.
     * Restricted to Platform Admin role.
     */
    @PatchMapping("/{propertyId}/activate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PropertyResponse> activateProperty(
            @PathVariable UUID propertyId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Property property = propertyService.activate(propertyId, actingUserId);
        return ResponseEntity.ok(PropertyResponse.from(property));
    }

    /**
     * Deactivates a property, preventing new ticket submissions.
     * Restricted to Platform Admin role.
     */
    @PatchMapping("/{propertyId}/deactivate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PropertyResponse> deactivateProperty(
            @PathVariable UUID propertyId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Property property = propertyService.deactivate(propertyId, actingUserId);
        return ResponseEntity.ok(PropertyResponse.from(property));
    }
}
