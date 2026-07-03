package com.strataresolve.property.controller;

import com.strataresolve.property.domain.Unit;
import com.strataresolve.property.dto.CreateUnitRequest;
import com.strataresolve.property.dto.UnitResponse;
import com.strataresolve.property.dto.UpdateUnitRequest;
import com.strataresolve.property.service.UnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * REST controller for unit management within a block and property.
 * Units represent individual dwellings or premises within a block.
 * All operations are restricted to Property Managers.
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/blocks/{blockId}/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    /**
     * Creates a new unit within the specified block.
     * Restricted to Property Manager role.
     */
    @PostMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<UnitResponse> createUnit(
            @PathVariable UUID propertyId,
            @PathVariable UUID blockId,
            @Valid @RequestBody CreateUnitRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Unit unit = unitService.create(propertyId, blockId, request, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(UnitResponse.from(unit));
    }

    /**
     * Updates an existing unit.
     * Restricted to Property Manager role.
     */
    @PutMapping("/{unitId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<UnitResponse> updateUnit(
            @PathVariable UUID propertyId,
            @PathVariable UUID blockId,
            @PathVariable UUID unitId,
            @Valid @RequestBody UpdateUnitRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Unit unit = unitService.update(unitId, propertyId, blockId, request, actingUserId);
        return ResponseEntity.ok(UnitResponse.from(unit));
    }

    /**
     * Retrieves a unit by ID.
     * Restricted to Property Manager role.
     */
    @GetMapping("/{unitId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<UnitResponse> getUnit(
            @PathVariable UUID propertyId,
            @PathVariable UUID blockId,
            @PathVariable UUID unitId) {
        Unit unit = unitService.findById(unitId);
        return ResponseEntity.ok(UnitResponse.from(unit));
    }

    /**
     * Lists all units within a block.
     * Restricted to Property Manager role.
     */
    @GetMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<List<UnitResponse>> listUnits(
            @PathVariable UUID propertyId,
            @PathVariable UUID blockId) {
        List<UnitResponse> units = unitService.findByBlockId(blockId).stream()
                .map(UnitResponse::from)
                .toList();
        return ResponseEntity.ok(units);
    }
}
