package com.strataresolve.vendor.service;

import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.PropertyConfigChangedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.DuplicateResourceException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.vendor.domain.Vendor;
import com.strataresolve.vendor.dto.CreateVendorRequest;
import com.strataresolve.vendor.dto.UpdateVendorRequest;
import com.strataresolve.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing vendor registration, association with properties, and activation/deactivation.
 */
@Service
@Transactional
public class VendorService {

    private final VendorRepository vendorRepository;
    private final PropertyRepository propertyRepository;
    private final DomainEventPublisher eventPublisher;

    public VendorService(VendorRepository vendorRepository,
                         PropertyRepository propertyRepository,
                         DomainEventPublisher eventPublisher) {
        this.vendorRepository = vendorRepository;
        this.propertyRepository = propertyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Registers a new vendor and associates it with a property.
     *
     * @param propertyId the property to associate the vendor with
     * @param request the vendor creation request
     * @param actingUserId the user performing the registration
     * @return the created vendor
     * @throws ResourceNotFoundException if the property does not exist
     * @throws DuplicateResourceException if a vendor with the same name exists in the property
     */
    public Vendor register(UUID propertyId, CreateVendorRequest request, UUID actingUserId) {
        validatePropertyExists(propertyId);

        if (vendorRepository.existsByPropertyIdAndName(propertyId, request.name())) {
            throw new DuplicateResourceException(
                    "A vendor with name '" + request.name() + "' already exists in this property");
        }

        Vendor vendor = Vendor.builder()
                .name(request.name())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .build();
        vendor.setPropertyId(propertyId);

        Vendor saved = vendorRepository.save(vendor);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Vendor", saved.getId(),
                "CREATED", null, formatVendorSummary(saved)
        ));

        return saved;
    }

    /**
     * Updates an existing vendor's details.
     *
     * @param vendorId the vendor to update
     * @param propertyId the property context
     * @param request the update request
     * @param actingUserId the user performing the update
     * @return the updated vendor
     */
    public Vendor update(UUID vendorId, UUID propertyId, UpdateVendorRequest request, UUID actingUserId) {
        Vendor vendor = findByIdAndPropertyOrThrow(vendorId, propertyId);

        // Validate name uniqueness if name is changing
        if (!vendor.getName().equals(request.name()) &&
                vendorRepository.existsByPropertyIdAndName(propertyId, request.name())) {
            throw new DuplicateResourceException(
                    "A vendor with name '" + request.name() + "' already exists in this property");
        }

        String previousValue = formatVendorSummary(vendor);

        vendor.setName(request.name());
        vendor.setContactEmail(request.contactEmail());
        vendor.setContactPhone(request.contactPhone());

        Vendor saved = vendorRepository.save(vendor);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Vendor", saved.getId(),
                "UPDATED", previousValue, formatVendorSummary(saved)
        ));

        return saved;
    }

    /**
     * Deactivates a vendor, preventing new work order assignments.
     *
     * @param vendorId the vendor to deactivate
     * @param propertyId the property context
     * @param actingUserId the user performing the action
     * @return the deactivated vendor
     */
    public Vendor deactivate(UUID vendorId, UUID propertyId, UUID actingUserId) {
        Vendor vendor = findByIdAndPropertyOrThrow(vendorId, propertyId);

        if (!vendor.isActive()) {
            throw new BusinessRuleViolationException("Vendor is already inactive");
        }

        String previousValue = formatVendorSummary(vendor);
        vendor.deactivate();
        Vendor saved = vendorRepository.save(vendor);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Vendor", saved.getId(),
                "DEACTIVATED", previousValue, formatVendorSummary(saved)
        ));

        return saved;
    }

    /**
     * Reactivates a vendor, allowing new work order assignments.
     *
     * @param vendorId the vendor to activate
     * @param propertyId the property context
     * @param actingUserId the user performing the action
     * @return the activated vendor
     */
    public Vendor activate(UUID vendorId, UUID propertyId, UUID actingUserId) {
        Vendor vendor = findByIdAndPropertyOrThrow(vendorId, propertyId);

        if (vendor.isActive()) {
            throw new BusinessRuleViolationException("Vendor is already active");
        }

        String previousValue = formatVendorSummary(vendor);
        vendor.activate();
        Vendor saved = vendorRepository.save(vendor);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Vendor", saved.getId(),
                "ACTIVATED", previousValue, formatVendorSummary(saved)
        ));

        return saved;
    }

    /**
     * Finds a vendor by ID within the given property context.
     *
     * @param vendorId the vendor ID
     * @param propertyId the property context
     * @return the vendor
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Vendor findById(UUID vendorId, UUID propertyId) {
        return findByIdAndPropertyOrThrow(vendorId, propertyId);
    }

    /**
     * Finds all vendors belonging to a property.
     *
     * @param propertyId the property ID
     * @return list of all vendors for the property
     */
    @Transactional(readOnly = true)
    public List<Vendor> findByPropertyId(UUID propertyId) {
        return vendorRepository.findByPropertyId(propertyId);
    }

    /**
     * Finds all active vendors belonging to a property.
     *
     * @param propertyId the property ID
     * @return list of active vendors for the property
     */
    @Transactional(readOnly = true)
    public List<Vendor> findActiveByPropertyId(UUID propertyId) {
        return vendorRepository.findByPropertyIdAndIsActiveTrue(propertyId);
    }

    /**
     * Deletes a vendor from a property.
     *
     * @param vendorId the vendor to delete
     * @param propertyId the property context
     * @param actingUserId the user performing the action
     */
    public void delete(UUID vendorId, UUID propertyId, UUID actingUserId) {
        Vendor vendor = findByIdAndPropertyOrThrow(vendorId, propertyId);

        String previousValue = formatVendorSummary(vendor);
        vendorRepository.delete(vendor);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, propertyId,
                "Vendor", vendorId,
                "DELETED", previousValue, null
        ));
    }

    private Vendor findByIdAndPropertyOrThrow(UUID vendorId, UUID propertyId) {
        return vendorRepository.findByIdAndPropertyId(vendorId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));
    }

    private void validatePropertyExists(UUID propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property", propertyId);
        }
    }

    private String formatVendorSummary(Vendor vendor) {
        return String.format("{\"name\":\"%s\",\"contactEmail\":\"%s\",\"contactPhone\":\"%s\",\"isActive\":%s}",
                vendor.getName(),
                vendor.getContactEmail() != null ? vendor.getContactEmail() : "",
                vendor.getContactPhone() != null ? vendor.getContactPhone() : "",
                vendor.isActive());
    }
}
