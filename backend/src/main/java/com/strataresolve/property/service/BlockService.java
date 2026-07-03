package com.strataresolve.property.service;

import com.strataresolve.property.domain.Block;
import com.strataresolve.property.dto.CreateBlockRequest;
import com.strataresolve.property.dto.UpdateBlockRequest;
import com.strataresolve.property.repository.BlockRepository;
import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.PropertyConfigChangedEvent;
import com.strataresolve.shared.exception.DuplicateResourceException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing block CRUD operations within a property context.
 */
@Service
@Transactional
public class BlockService {

    private final BlockRepository blockRepository;
    private final PropertyRepository propertyRepository;
    private final DomainEventPublisher eventPublisher;

    public BlockService(BlockRepository blockRepository,
                        PropertyRepository propertyRepository,
                        DomainEventPublisher eventPublisher) {
        this.blockRepository = blockRepository;
        this.propertyRepository = propertyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new block within the specified property.
     *
     * @param propertyId the property to create the block in
     * @param request the creation request
     * @param actingUserId the user performing the action
     * @return the created block
     * @throws ResourceNotFoundException if the property does not exist
     * @throws DuplicateResourceException if a block with the same name exists in the property
     */
    public Block create(UUID propertyId, CreateBlockRequest request, UUID actingUserId) {
        validatePropertyExists(propertyId);

        if (blockRepository.existsByPropertyIdAndName(propertyId, request.name())) {
            throw new DuplicateResourceException(
                    "A block with name '" + request.name() + "' already exists in this property");
        }

        Block block = Block.builder()
                .name(request.name())
                .label(request.label())
                .build();
        block.setPropertyId(propertyId);

        Block saved = blockRepository.save(block);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Block", saved.getId(),
                "CREATED", null, formatBlockSummary(saved)
        ));

        return saved;
    }

    /**
     * Updates an existing block.
     *
     * @param blockId the block to update
     * @param propertyId the property the block belongs to
     * @param request the update request
     * @param actingUserId the user performing the action
     * @return the updated block
     */
    public Block update(UUID blockId, UUID propertyId, UpdateBlockRequest request, UUID actingUserId) {
        Block block = findByIdOrThrow(blockId);

        // Validate name uniqueness if name is changing
        if (!block.getName().equals(request.name()) &&
                blockRepository.existsByPropertyIdAndName(propertyId, request.name())) {
            throw new DuplicateResourceException(
                    "A block with name '" + request.name() + "' already exists in this property");
        }

        String previousValue = formatBlockSummary(block);

        block.setName(request.name());
        block.setLabel(request.label());

        Block saved = blockRepository.save(block);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Block", saved.getId(),
                "UPDATED", previousValue, formatBlockSummary(saved)
        ));

        return saved;
    }

    /**
     * Finds a block by its ID.
     *
     * @param blockId the block ID
     * @return the block
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Block findById(UUID blockId) {
        return findByIdOrThrow(blockId);
    }

    /**
     * Finds all blocks belonging to a property.
     *
     * @param propertyId the property ID
     * @return list of blocks
     */
    @Transactional(readOnly = true)
    public List<Block> findByPropertyId(UUID propertyId) {
        return blockRepository.findByPropertyId(propertyId);
    }

    /**
     * Deletes a block by its ID.
     *
     * @param blockId the block to delete
     * @param propertyId the property context
     * @param actingUserId the user performing the action
     */
    public void delete(UUID blockId, UUID propertyId, UUID actingUserId) {
        Block block = findByIdOrThrow(blockId);

        String previousValue = formatBlockSummary(block);
        blockRepository.delete(block);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Block", blockId,
                "DELETED", previousValue, null
        ));
    }

    private Block findByIdOrThrow(UUID blockId) {
        return blockRepository.findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException("Block", blockId));
    }

    private void validatePropertyExists(UUID propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property", propertyId);
        }
    }

    private String formatBlockSummary(Block block) {
        return String.format("{\"name\":\"%s\",\"label\":\"%s\"}",
                block.getName(), block.getLabel() != null ? block.getLabel() : "");
    }
}
