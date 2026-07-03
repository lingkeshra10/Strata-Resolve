package com.strataresolve.property.repository;

import com.strataresolve.property.domain.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Property entities.
 * Property is the tenant itself, so queries here are not tenant-filtered.
 */
@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    /**
     * Find a property by its unique code.
     */
    Optional<Property> findByCode(String code);

    /**
     * Check if a property with the given code already exists.
     */
    boolean existsByCode(String code);
}
