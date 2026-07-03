package com.strataresolve.vendor.repository;

import com.strataresolve.vendor.domain.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Vendor entities.
 * Provides tenant-filtered queries scoped by property_id.
 */
@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    /**
     * Find all vendors belonging to a specific property.
     */
    List<Vendor> findByPropertyId(UUID propertyId);

    /**
     * Find all active vendors belonging to a specific property.
     */
    List<Vendor> findByPropertyIdAndIsActiveTrue(UUID propertyId);

    /**
     * Find a vendor by ID and property, ensuring tenant isolation.
     */
    Optional<Vendor> findByIdAndPropertyId(UUID id, UUID propertyId);

    /**
     * Check if a vendor with the given name already exists within a property.
     */
    boolean existsByPropertyIdAndName(UUID propertyId, String name);
}
