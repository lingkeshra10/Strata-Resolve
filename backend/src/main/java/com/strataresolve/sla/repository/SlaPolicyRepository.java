package com.strataresolve.sla.repository;

import com.strataresolve.sla.domain.SlaPolicy;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for SlaPolicy entities.
 * Provides custom lookup methods for policy resolution with fallback logic.
 */
@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {

    /**
     * Find all SLA policies belonging to a specific property.
     */
    List<SlaPolicy> findByPropertyId(UUID propertyId);

    /**
     * Find the specific SLA policy matching property, category, and priority exactly.
     */
    Optional<SlaPolicy> findByPropertyIdAndCategoryAndPriority(
            UUID propertyId, Category category, Priority priority);

    /**
     * Find the default SLA policy for a property (is_default = true).
     */
    Optional<SlaPolicy> findByPropertyIdAndIsDefaultTrue(UUID propertyId);

    /**
     * Find a policy matching property and category with null priority (category-level default).
     */
    Optional<SlaPolicy> findByPropertyIdAndCategoryAndPriorityIsNull(
            UUID propertyId, Category category);

    /**
     * Find a policy matching property and priority with null category (priority-level default).
     */
    Optional<SlaPolicy> findByPropertyIdAndCategoryIsNullAndPriority(
            UUID propertyId, Priority priority);

    /**
     * Check if a default policy already exists for a property.
     */
    boolean existsByPropertyIdAndIsDefaultTrue(UUID propertyId);

    /**
     * Check if a policy with the exact category/priority combination exists for a property.
     */
    boolean existsByPropertyIdAndCategoryAndPriority(
            UUID propertyId, Category category, Priority priority);
}
