package com.strataresolve.identity.repository;

import com.strataresolve.identity.domain.Membership;
import com.strataresolve.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Membership entities.
 * Provides queries for active memberships by user and/or property.
 */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    /**
     * Find all active memberships for a given user across all properties.
     */
    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.isActive = true")
    List<Membership> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Find all active memberships for a given property.
     */
    @Query("SELECT m FROM Membership m WHERE m.propertyId = :propertyId AND m.isActive = true")
    List<Membership> findActiveByPropertyId(@Param("propertyId") UUID propertyId);

    /**
     * Find all active memberships for a given user within a specific property.
     */
    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.propertyId = :propertyId AND m.isActive = true")
    List<Membership> findActiveByUserIdAndPropertyId(@Param("userId") UUID userId, @Param("propertyId") UUID propertyId);

    /**
     * Check if a user has any active membership for a given property.
     */
    @Query("SELECT COUNT(m) > 0 FROM Membership m WHERE m.userId = :userId AND m.propertyId = :propertyId AND m.isActive = true")
    boolean hasActiveMembership(@Param("userId") UUID userId, @Param("propertyId") UUID propertyId);

    /**
     * Check if a user already has an active membership with a specific role for a property.
     */
    @Query("SELECT COUNT(m) > 0 FROM Membership m WHERE m.userId = :userId AND m.propertyId = :propertyId AND m.role = :role AND m.isActive = true")
    boolean existsActiveByUserIdAndPropertyIdAndRole(@Param("userId") UUID userId, @Param("propertyId") UUID propertyId, @Param("role") Role role);

    /**
     * Find a specific active membership by user, property, and role.
     */
    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.propertyId = :propertyId AND m.role = :role AND m.isActive = true")
    Optional<Membership> findActiveByUserIdAndPropertyIdAndRole(@Param("userId") UUID userId, @Param("propertyId") UUID propertyId, @Param("role") Role role);

    /**
     * Find all memberships (including inactive) for a user within a property, ordered by creation date.
     */
    @Query("SELECT m FROM Membership m WHERE m.userId = :userId AND m.propertyId = :propertyId ORDER BY m.createdAt DESC")
    List<Membership> findAllByUserIdAndPropertyId(@Param("userId") UUID userId, @Param("propertyId") UUID propertyId);

    /**
     * Find all active memberships for a given vendor (VENDOR_TECHNICIAN or VENDOR_ADMIN roles).
     */
    @Query("SELECT m FROM Membership m WHERE m.vendorId = :vendorId AND m.isActive = true")
    List<Membership> findActiveByVendorId(@Param("vendorId") UUID vendorId);
}
