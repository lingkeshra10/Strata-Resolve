package com.strataresolve.property.repository;

import com.strataresolve.property.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Unit entities.
 * Units are tenant-aware — the Hibernate tenant filter automatically restricts
 * queries to the current property context.
 */
@Repository
public interface UnitRepository extends JpaRepository<Unit, UUID> {

    /**
     * Find all units within a specific block.
     */
    List<Unit> findByBlockId(UUID blockId);

    /**
     * Find all units belonging to a specific property.
     */
    List<Unit> findByPropertyId(UUID propertyId);

    /**
     * Check if a unit with the given unit number already exists within a block.
     * Used to enforce unit number uniqueness within a block before attempting to persist.
     */
    boolean existsByBlockIdAndUnitNumber(UUID blockId, String unitNumber);
}
