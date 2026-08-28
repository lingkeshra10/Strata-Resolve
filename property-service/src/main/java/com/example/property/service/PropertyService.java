package com.example.property.service;

import com.example.property.domain.Property;
import com.example.property.domain.PropertyStatus;
import com.example.property.dto.CreatePropertyRequest;
import com.example.property.dto.UpdatePropertyRequest;
import com.example.property.repository.PropertyRepository;
import com.strataresolve.common.event.DomainEventPublisher;
import com.strataresolve.common.event.PropertyConfigChangedEvent;
import com.strataresolve.common.exception.BusinessRuleViolationException;
import com.strataresolve.common.exception.DuplicateResourceException;
import com.strataresolve.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing property lifecycle: creation, update, activation, and deactivation.
 * Properties are the top-level tenant boundary — they are not filtered by tenant context.
 */
@Service
@Transactional
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final DomainEventPublisher eventPublisher;

    public PropertyService(PropertyRepository propertyRepository,
                           DomainEventPublisher eventPublisher) {
        this.propertyRepository = propertyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new property with ACTIVE status.
     *
     * @param request the creation request
     * @param actingUserId the user performing the action
     * @return the created property
     * @throws DuplicateResourceException if a property with the same code already exists
     */
    public Property create(CreatePropertyRequest request, UUID actingUserId) {
        if (propertyRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException(
                    "A property with code '" + request.code() + "' already exists");
        }

        Property property = Property.builder()
                .name(request.name())
                .code(request.code())
                .address(request.address())
                .timezone(request.timezone())
                .status(PropertyStatus.ACTIVE)
                .build();

        Property saved = propertyRepository.save(property);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, saved.getId(),
                "Property", saved.getId(),
                "CREATED", null, formatPropertySummary(saved)
        ));

        return saved;
    }

    /**
     * Updates an existing property's name, address, and timezone.
     *
     * @param propertyId the property to update
     * @param request the update request
     * @param actingUserId the user performing the action
     * @return the updated property
     */
    public Property update(UUID propertyId, UpdatePropertyRequest request, UUID actingUserId) {
        Property property = findByIdOrThrow(propertyId);

        String previousValue = formatPropertySummary(property);

        property.setName(request.name());
        property.setAddress(request.address());
        property.setTimezone(request.timezone());

        Property saved = propertyRepository.save(property);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, saved.getId(),
                "Property", saved.getId(),
                "UPDATED", previousValue, formatPropertySummary(saved)
        ));

        return saved;
    }

    /**
     * Finds a property by its ID.
     *
     * @param propertyId the property ID
     * @return the property
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Property findById(UUID propertyId) {
        return findByIdOrThrow(propertyId);
    }

    /**
     * Returns all properties.
     */
    @Transactional(readOnly = true)
    public List<Property> findAll() {
        return propertyRepository.findAll();
    }

    /**
     * Activates a property, allowing new ticket submissions.
     *
     * @param propertyId the property to activate
     * @param actingUserId the user performing the action
     * @return the activated property
     */
    public Property activate(UUID propertyId, UUID actingUserId) {
        Property property = findByIdOrThrow(propertyId);

        if (property.isActive()) {
            throw new BusinessRuleViolationException("Property is already active");
        }

        String previousStatus = property.getStatus().name();
        property.activate();
        Property saved = propertyRepository.save(property);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, saved.getId(),
                "Property", saved.getId(),
                "ACTIVATED", previousStatus, saved.getStatus().name()
        ));

        return saved;
    }

    /**
     * Deactivates a property, preventing new ticket submissions while preserving existing data.
     *
     * @param propertyId the property to deactivate
     * @param actingUserId the user performing the action
     * @return the deactivated property
     */
    public Property deactivate(UUID propertyId, UUID actingUserId) {
        Property property = findByIdOrThrow(propertyId);

        if (!property.isActive()) {
            throw new BusinessRuleViolationException("Property is already inactive");
        }

        String previousStatus = property.getStatus().name();
        property.deactivate();
        Property saved = propertyRepository.save(property);

        eventPublisher.publish(new PropertyConfigChangedEvent(
                actingUserId, saved.getId(),
                "Property", saved.getId(),
                "DEACTIVATED", previousStatus, saved.getStatus().name()
        ));

        return saved;
    }

    private Property findByIdOrThrow(UUID propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));
    }

    private String formatPropertySummary(Property property) {
        return String.format("{\"name\":\"%s\",\"code\":\"%s\",\"address\":\"%s\",\"timezone\":\"%s\",\"status\":\"%s\"}",
                property.getName(), property.getCode(), property.getAddress(),
                property.getTimezone(), property.getStatus());
    }
}
