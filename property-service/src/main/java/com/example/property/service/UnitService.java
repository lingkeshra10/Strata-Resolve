package com.example.property.service;

import com.example.property.domain.OccupancyStatus;
import com.example.property.domain.Unit;
import com.example.property.dto.CreateUnitRequest;
import com.example.property.dto.UpdateUnitRequest;
import com.example.property.repository.BlockRepository;
import com.example.property.repository.UnitRepository;
import com.strataresolve.common.event.DomainEventPublisher;
import com.strataresolve.common.event.PropertyConfigChangedEvent;
import com.strataresolve.common.exception.DuplicateResourceException;
import com.strataresolve.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing unit CRUD operations within a block/property context.
 */
@Service
@Transactional
public class UnitService {

    private final UnitRepository unitRepository;
    private final BlockRepository blockRepository;
    private final DomainEventPublisher eventPublisher;

    public UnitService(UnitRepository unitRepository,
                       BlockRepository blockRepository,
                       DomainEventPublisher eventPublisher) {
        this.unitRepository = unitRepository;
        this.blockRepository = blockRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new unit within the specified block and property.
     *
     * @param propertyId the property the unit belongs to
     * @param blockId the block the unit belongs to
     * @param request the creation request
     * @param actingUserId the user performing the action
     * @return the created unit
     * @throws ResourceNotFoundException if the block does not exist
     * @throws DuplicateResourceException if a unit with the same number exists in the block
     */
    public Unit create(UUID propertyId, UUID blockId, CreateUnitRequest request, UUID actingUserId) {
        validateBlockExists(blockId, propertyId);

        if (unitRepository.existsByBlockIdAndUnitNumber(blockId, request.unitNumber())) {
            throw new DuplicateResourceException(
                    "A unit with number '" + request.unitNumber() + "' already exists in this block");
        }

        Unit unit = Unit.builder()
                .blockId(blockId)
                .unitNumber(request.unitNumber())
                .floor(request.floor())
                .type(request.type())
                .occupancyStatus(request.occupancyStatus() != null ? request.occupancyStatus() : OccupancyStatus.VACANT)
                .build();
        unit.setPropertyId(propertyId);

        Unit saved = unitRepository.save(unit);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Unit", saved.getId(),
                "CREATED", null, formatUnitSummary(saved)
        ));

        return saved;
    }

    /**
     * Updates an existing unit.
     *
     * @param unitId the unit to update
     * @param propertyId the property context
     * @param blockId the block context
     * @param request the update request
     * @param actingUserId the user performing the action
     * @return the updated unit
     */
    public Unit update(UUID unitId, UUID propertyId, UUID blockId,
                       UpdateUnitRequest request, UUID actingUserId) {
        Unit unit = findByIdOrThrow(unitId, blockId, propertyId);

        // Validate unit number uniqueness if changing
        if (!unit.getUnitNumber().equals(request.unitNumber()) &&
                unitRepository.existsByBlockIdAndUnitNumber(blockId, request.unitNumber())) {
            throw new DuplicateResourceException(
                    "A unit with number '" + request.unitNumber() + "' already exists in this block");
        }

        String previousValue = formatUnitSummary(unit);

        unit.setUnitNumber(request.unitNumber());
        unit.setFloor(request.floor());
        unit.setType(request.type());
        unit.setOccupancyStatus(request.occupancyStatus());

        Unit saved = unitRepository.save(unit);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Unit", saved.getId(),
                "UPDATED", previousValue, formatUnitSummary(saved)
        ));

        return saved;
    }

    /**
     * Finds a unit by its ID.
     *
     * @param unitId the unit ID
     * @return the unit
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Unit findById(UUID unitId) {
        return findByIdOrThrow(unitId);
    }

    /**
     * Finds all units within a specific block.
     *
     * @param blockId the block ID
     * @return list of units
     */
    @Transactional(readOnly = true)
    public List<Unit> findByBlockId(UUID blockId) {
        return unitRepository.findByBlockId(blockId);
    }

    /**
     * Finds all units within a specific property.
     *
     * @param propertyId the property ID
     * @return list of units
     */
    @Transactional(readOnly = true)
    public List<Unit> findByPropertyId(UUID propertyId) {
        return unitRepository.findByPropertyId(propertyId);
    }

    private Unit findByIdOrThrow(UUID unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", unitId));
    }

    private Unit findByIdOrThrow(
            UUID unitId,
            UUID blockId,
            UUID propertyId) {
        return unitRepository
                .findByIdAndBlockIdAndPropertyId(
                        unitId,
                        blockId,
                        propertyId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException("Unit", unitId));
    }

    private void validateBlockExists(UUID blockId) {
        if (!blockRepository.existsById(blockId)) {
            throw new ResourceNotFoundException("Block", blockId);
        }
    }

    private void validateBlockExists(UUID blockId, UUID propertyId) {
        if (!blockRepository.existsByIdAndPropertyId(blockId, propertyId)) {
            throw new ResourceNotFoundException("Block", blockId);
        }
    }

    private String formatUnitSummary(Unit unit) {
        return String.format("{\"unitNumber\":\"%s\",\"floor\":%s,\"type\":\"%s\",\"occupancyStatus\":\"%s\"}",
                unit.getUnitNumber(),
                unit.getFloor() != null ? unit.getFloor() : "null",
                unit.getType(),
                unit.getOccupancyStatus());
    }
}
