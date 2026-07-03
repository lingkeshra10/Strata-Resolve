package com.strataresolve.vendor.repository;

import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.domain.WorkOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for WorkOrder entities.
 * Provides tenant-filtered queries scoped by property_id.
 */
@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    /**
     * Find a work order by ID and property, ensuring tenant isolation.
     */
    Optional<WorkOrder> findByIdAndPropertyId(UUID id, UUID propertyId);

    /**
     * Find all work orders for a specific vendor within a property.
     */
    List<WorkOrder> findByVendorIdAndPropertyId(UUID vendorId, UUID propertyId);

    /**
     * Find all work orders for a specific ticket.
     */
    Optional<WorkOrder> findByTicketIdAndPropertyId(UUID ticketId, UUID propertyId);

    /**
     * Find all work orders for a vendor with a specific status.
     */
    List<WorkOrder> findByVendorIdAndPropertyIdAndStatus(UUID vendorId, UUID propertyId, WorkOrderStatus status);

    /**
     * Find all work orders visible to a vendor (all work orders assigned to that vendor in the property).
     * This supports Requirement 19.3: Work orders visible to all active Vendor_Technicians of the vendor.
     */
    @Query("SELECT wo FROM WorkOrder wo WHERE wo.vendorId = :vendorId AND wo.propertyId = :propertyId ORDER BY wo.createdAt DESC")
    List<WorkOrder> findAllByVendorForTechnicians(@Param("vendorId") UUID vendorId, @Param("propertyId") UUID propertyId);

    /**
     * Find all work orders for a property.
     */
    List<WorkOrder> findByPropertyId(UUID propertyId);

    /**
     * Check if a work order already exists for a given ticket in a property.
     */
    boolean existsByTicketIdAndPropertyId(UUID ticketId, UUID propertyId);
}
