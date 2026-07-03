package com.strataresolve.vendor.service;

import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.WorkOrderAttachmentUploadedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.InvalidTransitionException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.shared.filestorage.FileMetadata;
import com.strataresolve.shared.filestorage.FileReference;
import com.strataresolve.shared.filestorage.FileStorageService;
import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.repository.AttachmentRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import com.strataresolve.vendor.domain.Vendor;
import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.domain.WorkOrderStatus;
import com.strataresolve.vendor.repository.VendorRepository;
import com.strataresolve.vendor.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service managing work order lifecycle: creation, acceptance, completion, cancellation.
 * Enforces vendor validation, makes work orders visible to all active vendor technicians,
 * and supports quotation/completion evidence uploads with notification to Property Manager.
 */
@Service
@Transactional
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final VendorRepository vendorRepository;
    private final MembershipRepository membershipRepository;
    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final DomainEventPublisher eventPublisher;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            VendorRepository vendorRepository,
                            MembershipRepository membershipRepository,
                            AttachmentRepository attachmentRepository,
                            FileStorageService fileStorageService,
                            DomainEventPublisher eventPublisher) {
        this.workOrderRepository = workOrderRepository;
        this.vendorRepository = vendorRepository;
        this.membershipRepository = membershipRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new work order for a ticket assigned to a vendor.
     *
     * @param ticketId     the ticket to create the work order for
     * @param vendorId     the vendor to assign the work order to
     * @param propertyId   the property context
     * @param actingUserId the user creating the work order (typically a Property Manager)
     * @return the created work order
     * @throws ResourceNotFoundException     if the vendor does not exist
     * @throws BusinessRuleViolationException if the vendor is inactive or a work order already exists for the ticket
     */
    public WorkOrder create(UUID ticketId, UUID vendorId, UUID propertyId, UUID actingUserId) {
        // Validate vendor exists and is active within the property
        Vendor vendor = vendorRepository.findByIdAndPropertyId(vendorId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));

        if (!vendor.isActive()) {
            throw new BusinessRuleViolationException(
                    "Cannot create work order: vendor '" + vendor.getName() + "' is inactive");
        }

        // Check if a work order already exists for this ticket in this property
        if (workOrderRepository.existsByTicketIdAndPropertyId(ticketId, propertyId)) {
            throw new BusinessRuleViolationException(
                    "A work order already exists for this ticket");
        }

        WorkOrder workOrder = WorkOrder.builder()
                .ticketId(ticketId)
                .vendorId(vendorId)
                .status(WorkOrderStatus.CREATED)
                .build();
        workOrder.setPropertyId(propertyId);

        return workOrderRepository.save(workOrder);
    }

    /**
     * Transitions a work order to ACCEPTED status.
     * Typically called by a vendor technician accepting the work.
     *
     * @param workOrderId  the work order to accept
     * @param propertyId   the property context
     * @param actingUserId the user accepting the work order
     * @return the updated work order
     */
    public WorkOrder accept(UUID workOrderId, UUID propertyId, UUID actingUserId) {
        WorkOrder workOrder = findByIdAndPropertyOrThrow(workOrderId, propertyId);
        try {
            workOrder.accept();
        } catch (IllegalStateException e) {
            throw new InvalidTransitionException(workOrder.getStatus().name(), WorkOrderStatus.ACCEPTED.name());
        }
        return workOrderRepository.save(workOrder);
    }

    /**
     * Transitions a work order to IN_PROGRESS status.
     *
     * @param workOrderId  the work order to start
     * @param propertyId   the property context
     * @param actingUserId the user starting the work
     * @return the updated work order
     */
    public WorkOrder startWork(UUID workOrderId, UUID propertyId, UUID actingUserId) {
        WorkOrder workOrder = findByIdAndPropertyOrThrow(workOrderId, propertyId);
        try {
            workOrder.startWork();
        } catch (IllegalStateException e) {
            throw new InvalidTransitionException(workOrder.getStatus().name(), WorkOrderStatus.IN_PROGRESS.name());
        }
        return workOrderRepository.save(workOrder);
    }

    /**
     * Transitions a work order to COMPLETED status.
     *
     * @param workOrderId  the work order to complete
     * @param propertyId   the property context
     * @param actingUserId the user completing the work
     * @return the updated work order
     */
    public WorkOrder complete(UUID workOrderId, UUID propertyId, UUID actingUserId) {
        WorkOrder workOrder = findByIdAndPropertyOrThrow(workOrderId, propertyId);
        try {
            workOrder.complete();
        } catch (IllegalStateException e) {
            throw new InvalidTransitionException(workOrder.getStatus().name(), WorkOrderStatus.COMPLETED.name());
        }
        return workOrderRepository.save(workOrder);
    }

    /**
     * Transitions a work order to CANCELLED status.
     *
     * @param workOrderId  the work order to cancel
     * @param propertyId   the property context
     * @param actingUserId the user cancelling the work order
     * @return the updated work order
     */
    public WorkOrder cancel(UUID workOrderId, UUID propertyId, UUID actingUserId) {
        WorkOrder workOrder = findByIdAndPropertyOrThrow(workOrderId, propertyId);
        try {
            workOrder.cancel();
        } catch (IllegalStateException e) {
            throw new InvalidTransitionException(workOrder.getStatus().name(), WorkOrderStatus.CANCELLED.name());
        }
        return workOrderRepository.save(workOrder);
    }

    /**
     * Transitions a work order to the specified target status.
     *
     * @param workOrderId  the work order to transition
     * @param propertyId   the property context
     * @param targetStatus the desired target status
     * @param actingUserId the user performing the transition
     * @return the updated work order
     */
    public WorkOrder transition(UUID workOrderId, UUID propertyId, WorkOrderStatus targetStatus, UUID actingUserId) {
        return switch (targetStatus) {
            case ACCEPTED -> accept(workOrderId, propertyId, actingUserId);
            case IN_PROGRESS -> startWork(workOrderId, propertyId, actingUserId);
            case COMPLETED -> complete(workOrderId, propertyId, actingUserId);
            case CANCELLED -> cancel(workOrderId, propertyId, actingUserId);
            case CREATED -> throw new InvalidTransitionException(
                    "Cannot transition to CREATED status");
        };
    }

    /**
     * Retrieves all work orders visible to the vendor technicians of a given vendor.
     * Implements Requirement 19.3: Work orders are visible to all active Vendor_Technicians
     * of the assigned vendor.
     *
     * @param vendorId   the vendor ID
     * @param propertyId the property context
     * @return list of work orders for the vendor
     */
    @Transactional(readOnly = true)
    public List<WorkOrder> findByVendor(UUID vendorId, UUID propertyId) {
        return workOrderRepository.findAllByVendorForTechnicians(vendorId, propertyId);
    }

    /**
     * Retrieves all work orders visible to a specific vendor technician.
     * Determines the vendor from the user's active membership.
     *
     * @param userId     the vendor technician user ID
     * @param propertyId the property context
     * @return list of work orders visible to the technician
     */
    @Transactional(readOnly = true)
    public List<WorkOrder> findByVendorTechnician(UUID userId, UUID propertyId) {
        UUID vendorId = resolveVendorIdForUser(userId, propertyId);
        return workOrderRepository.findAllByVendorForTechnicians(vendorId, propertyId);
    }

    /**
     * Retrieves a work order by ID within the property context.
     *
     * @param workOrderId the work order ID
     * @param propertyId  the property context
     * @return the work order
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public WorkOrder findById(UUID workOrderId, UUID propertyId) {
        return findByIdAndPropertyOrThrow(workOrderId, propertyId);
    }

    /**
     * Retrieves all work orders for a property.
     *
     * @param propertyId the property context
     * @return list of all work orders in the property
     */
    @Transactional(readOnly = true)
    public List<WorkOrder> findByPropertyId(UUID propertyId) {
        return workOrderRepository.findByPropertyId(propertyId);
    }

    /**
     * Uploads a quotation or completion evidence to a work order.
     * Stores the attachment and notifies the Property Manager via domain event.
     * Implements Requirement 19.5.
     *
     * @param workOrderId    the work order ID
     * @param propertyId     the property context
     * @param file           the uploaded file
     * @param attachmentType the type of attachment (e.g., "QUOTATION", "COMPLETION_EVIDENCE")
     * @param actingUserId   the vendor technician uploading the file
     * @return the stored attachment
     */
    public Attachment uploadEvidence(UUID workOrderId, UUID propertyId, MultipartFile file,
                                     String attachmentType, UUID actingUserId) {
        WorkOrder workOrder = findByIdAndPropertyOrThrow(workOrderId, propertyId);

        // Store the file
        FileMetadata metadata = new FileMetadata(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                Instant.now(),
                actingUserId
        );

        FileReference fileReference;
        try (InputStream inputStream = file.getInputStream()) {
            fileReference = fileStorageService.store(inputStream, metadata);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }

        // Create attachment record linked to the work order's ticket
        Attachment attachment = Attachment.builder()
                .ticketId(workOrder.getTicketId())
                .uploadedBy(actingUserId)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .storageReference(fileReference.getStorageReference())
                .build();

        Attachment savedAttachment = attachmentRepository.save(attachment);

        // Publish event to trigger notification to Property Manager
        eventPublisher.publish(new WorkOrderAttachmentUploadedEvent(
                actingUserId, propertyId,
                workOrderId, workOrder.getTicketId(),
                savedAttachment.getId(), attachmentType
        ));

        return savedAttachment;
    }

    /**
     * Resolves the vendor ID for a user based on their active membership.
     *
     * @param userId     the user ID
     * @param propertyId the property context
     * @return the vendor ID linked to the user's membership
     * @throws BusinessRuleViolationException if the user has no active vendor membership
     */
    private UUID resolveVendorIdForUser(UUID userId, UUID propertyId) {
        List<Membership> memberships = membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId);
        return memberships.stream()
                .filter(m -> m.getRole() == Role.VENDOR_TECHNICIAN || m.getRole() == Role.VENDOR_ADMIN)
                .filter(m -> m.getVendorId() != null)
                .findFirst()
                .map(Membership::getVendorId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "User does not have an active vendor membership for this property"));
    }

    private WorkOrder findByIdAndPropertyOrThrow(UUID workOrderId, UUID propertyId) {
        return workOrderRepository.findByIdAndPropertyId(workOrderId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", workOrderId));
    }
}
