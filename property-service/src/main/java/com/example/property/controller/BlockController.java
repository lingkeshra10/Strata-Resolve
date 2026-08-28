package com.example.property.controller;

import com.example.property.domain.Block;
import com.example.property.dto.BlockResponse;
import com.example.property.dto.CreateBlockRequest;
import com.example.property.dto.UpdateBlockRequest;
import com.example.property.service.BlockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for block management within a property.
 * Blocks represent physical towers or building sections.
 * All operations are restricted to Property Managers.
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    /**
     * Creates a new block within the specified property.
     * Restricted to Property Manager role.
     */
    @PostMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<BlockResponse> createBlock(
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateBlockRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Block block = blockService.create(propertyId, request, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(BlockResponse.from(block));
    }

    /**
     * Updates an existing block.
     * Restricted to Property Manager role.
     */
    @PutMapping("/{blockId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<BlockResponse> updateBlock(
            @PathVariable UUID propertyId,
            @PathVariable UUID blockId,
            @Valid @RequestBody UpdateBlockRequest request,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        Block block = blockService.update(blockId, propertyId, request, actingUserId);
        return ResponseEntity.ok(BlockResponse.from(block));
    }

    /**
     * Retrieves a block by ID.
     * Restricted to Property Manager role.
     */
    @GetMapping("/{blockId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<BlockResponse> getBlock(
            @PathVariable UUID propertyId,
            @PathVariable UUID blockId) {
        Block block = blockService.findById(blockId);
        return ResponseEntity.ok(BlockResponse.from(block));
    }

    /**
     * Lists all blocks within a property.
     * Restricted to Property Manager role.
     */
    @GetMapping
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<List<BlockResponse>> listBlocks(@PathVariable UUID propertyId) {
        List<BlockResponse> blocks = blockService.findByPropertyId(propertyId).stream()
                .map(BlockResponse::from)
                .toList();
        return ResponseEntity.ok(blocks);
    }

    /**
     * Deletes a block.
     * Restricted to Property Manager role.
     */
    @DeleteMapping("/{blockId}")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public ResponseEntity<Void> deleteBlock(
            @PathVariable UUID propertyId,
            @PathVariable UUID blockId,
            Authentication authentication) {
        UUID actingUserId = (UUID) authentication.getPrincipal();
        blockService.delete(blockId, propertyId, actingUserId);
        return ResponseEntity.noContent().build();
    }
}
