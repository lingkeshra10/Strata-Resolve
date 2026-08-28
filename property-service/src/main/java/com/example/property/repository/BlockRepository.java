package com.example.property.repository;

import com.example.property.domain.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Block entities.
 * Blocks are tenant-aware — the Hibernate tenant filter automatically restricts
 * queries to the current property context.
 */
@Repository
public interface BlockRepository extends JpaRepository<Block, UUID> {

    Optional<Block> findByIdAndPropertyId(UUID id, UUID propertyId);

    boolean existsByIdAndPropertyId(UUID id, UUID propertyId);

    /**
     * Find all blocks belonging to a specific property.
     */
    List<Block> findByPropertyId(UUID propertyId);

    /**
     * Check if a block with the given name already exists within a property.
     * Used to enforce name uniqueness within a property before attempting to persist.
     */
    boolean existsByPropertyIdAndName(UUID propertyId, String name);
}
