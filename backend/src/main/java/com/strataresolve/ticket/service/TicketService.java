package com.strataresolve.ticket.service;

import com.strataresolve.property.domain.Property;
import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.PriorityChangedEvent;
import com.strataresolve.shared.event.StatusChangedEvent;
import com.strataresolve.shared.event.TicketCreatedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.RateLimitExceededException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.sla.service.SlaCalculator;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.StatusHistory;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.dto.CreateTicketRequest;
import com.strataresolve.ticket.dto.DuplicateDetectionResult;
import com.strataresolve.ticket.dto.ReopenTicketRequest;
import com.strataresolve.ticket.dto.TransitionTicketStatusRequest;
import com.strataresolve.ticket.policy.StatusWorkflowEngine;
import com.strataresolve.ticket.repository.StatusHistoryRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.repository.MembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for ticket submission logic.
 * Validates the property is active, associates the ticket with the resident's unit and property,
 * generates a reference number, sets initial status to SUBMITTED, calculates SLA targets,
 * and publishes a TicketCreatedEvent on successful submission.
 */
@Service
@Transactional
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private static final Priority DEFAULT_PRIORITY = Priority.NORMAL;

    private final TicketRepository ticketRepository;
    private final PropertyRepository propertyRepository;
    private final MembershipRepository membershipRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ReferenceNumberGenerator referenceNumberGenerator;
    private final StatusWorkflowEngine statusWorkflowEngine;
    private final TicketProperties ticketProperties;
    private final DomainEventPublisher eventPublisher;
    private final DuplicateDetectionService duplicateDetectionService;
    private final SlaCalculator slaCalculator;

    public TicketService(TicketRepository ticketRepository,
                         PropertyRepository propertyRepository,
                         MembershipRepository membershipRepository,
                         StatusHistoryRepository statusHistoryRepository,
                         ReferenceNumberGenerator referenceNumberGenerator,
                         StatusWorkflowEngine statusWorkflowEngine,
                         TicketProperties ticketProperties,
                         DomainEventPublisher eventPublisher,
                         DuplicateDetectionService duplicateDetectionService,
                         SlaCalculator slaCalculator) {
        this.ticketRepository = ticketRepository;
        this.propertyRepository = propertyRepository;
        this.membershipRepository = membershipRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.referenceNumberGenerator = referenceNumberGenerator;
        this.statusWorkflowEngine = statusWorkflowEngine;
        this.ticketProperties = ticketProperties;
        this.eventPublisher = eventPublisher;
        this.duplicateDetectionService = duplicateDetectionService;
        this.slaCalculator = slaCalculator;
    }

    /**
     * Submits a new maintenance ticket.
     *
     * <p>Performs the following:
     * <ol>
     *   <li>Validates the property is active</li>
     *   <li>Enforces submission rate limiting per resident</li>
     *   <li>Resolves the resident's active membership to determine the unit</li>
     *   <li>Generates a unique reference number</li>
     *   <li>Sets initial status to SUBMITTED</li>
     *   <li>Calculates SLA targets (acknowledgement_due_at and resolution_due_at)</li>
     *   <li>Persists the ticket and initial status history</li>
     *   <li>Publishes a TicketCreatedEvent</li>
     * </ol>
     *
     * @param request      the ticket creation request
     * @param propertyId   the property context UUID
     * @param submittedBy  the UUID of the submitting resident
     * @return the persisted Ticket entity
     * @throws BusinessRuleViolationException if the property is inactive
     * @throws RateLimitExceededException if the resident has exceeded the submission rate limit
     * @throws ResourceNotFoundException if no active resident membership is found
     */
    public Ticket submitTicket(CreateTicketRequest request, UUID propertyId, UUID submittedBy) {
        // 1. Validate property is active
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));

        if (!property.isActive()) {
            throw new BusinessRuleViolationException(
                    "Cannot submit tickets to an inactive property. Property '" + property.getName() + "' is currently inactive.");
        }

        // 2. Enforce rate limiting on ticket submissions
        enforceSubmissionRateLimit(submittedBy);

        // 3. Resolve resident's unit from active membership
        List<Membership> activeMemberships = membershipRepository.findActiveByUserIdAndPropertyId(submittedBy, propertyId);
        Membership residentMembership = activeMemberships.stream()
                .filter(m -> m.getUnitId() != null)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active resident membership with a linked unit found for user in this property"));

        UUID unitId = residentMembership.getUnitId();

        // 4. Generate reference number
        String referenceNumber = referenceNumberGenerator.generateReferenceNumber();

        // 5. Determine priority (resident's suggestion or default)
        Priority priority = request.suggestedPriority() != null ? request.suggestedPriority() : DEFAULT_PRIORITY;

        // 6. Calculate SLA targets
        SlaCalculator.SlaTargets slaTargets = slaCalculator.calculateTargets(
                propertyId, property.getTimezone(), request.category(), priority);

        // 7. Build and persist ticket
        Ticket ticket = Ticket.builder()
                .submittedBy(submittedBy)
                .unitId(unitId)
                .referenceNumber(referenceNumber)
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .priority(priority)
                .status(TicketStatus.SUBMITTED)
                .location(request.location())
                .acknowledgementDueAt(slaTargets.acknowledgementDueAt())
                .resolutionDueAt(slaTargets.resolutionDueAt())
                .slaStatus(SlaStatus.ON_TRACK)
                .build();
        ticket.setPropertyId(propertyId);

        Ticket savedTicket = ticketRepository.save(ticket);

        // 7. Check for potential duplicates and flag if found (does NOT block submission)
        DuplicateDetectionResult duplicateResult = duplicateDetectionService.checkForDuplicates(
                propertyId, request.title(), request.category(), request.location());
        if (duplicateResult.flaggedAsDuplicate()) {
            duplicateDetectionService.flagAsDuplicate(savedTicket);
            log.info("Ticket {} flagged as potential duplicate. {} potential match(es) found.",
                    savedTicket.getId(), duplicateResult.potentialDuplicates().size());
        }

        // 8. Record initial status history
        StatusHistory initialHistory = StatusHistory.builder()
                .ticketId(savedTicket.getId())
                .previousStatus(TicketStatus.SUBMITTED)
                .newStatus(TicketStatus.SUBMITTED)
                .changedBy(submittedBy)
                .reason("Ticket submitted")
                .build();
        statusHistoryRepository.save(initialHistory);

        // 9. Publish domain event
        eventPublisher.publish(new TicketCreatedEvent(
                submittedBy,
                propertyId,
                savedTicket.getId(),
                unitId,
                referenceNumber,
                request.category().name(),
                priority.name()
        ));

        log.info("Ticket submitted: {} (ref: {}) for property {} by user {}",
                savedTicket.getId(), referenceNumber, propertyId, submittedBy);

        return savedTicket;
    }

    /**
     * Transitions a ticket's status to the specified target status.
     *
     * <p>Performs the following:
     * <ol>
     *   <li>Retrieves the ticket and validates it exists</li>
     *   <li>Validates the transition using the StatusWorkflowEngine</li>
     *   <li>Updates the ticket status</li>
     *   <li>Records acknowledged_at when transitioning to ACKNOWLEDGED</li>
     *   <li>Records resolved_at when transitioning to RESOLVED</li>
     *   <li>Persists a StatusHistory entry</li>
     *   <li>Publishes a StatusChangedEvent</li>
     * </ol>
     *
     * @param ticketId  the ticket to transition
     * @param request   the transition request containing target status and optional reason
     * @param actingUserId the UUID of the user performing the transition
     * @return the updated Ticket entity
     * @throws ResourceNotFoundException if the ticket is not found
     * @throws InvalidTransitionException if the transition is not allowed
     * @throws BusinessRuleViolationException if a reason is required but not provided
     */
    public Ticket transitionStatus(UUID ticketId, TransitionTicketStatusRequest request, UUID actingUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        TicketStatus previousStatus = ticket.getStatus();
        TicketStatus targetStatus = request.targetStatus();
        String reason = request.reason();

        // Validate that a reason is provided for transitions that require it
        if (requiresReason(targetStatus) && (reason == null || reason.isBlank())) {
            throw new BusinessRuleViolationException(
                    "A reason is required when transitioning to " + targetStatus.name());
        }

        // Validate the transition against the workflow policy
        statusWorkflowEngine.validateTransition(previousStatus, targetStatus);

        // Apply the status change
        ticket.setStatus(targetStatus);

        // Record timestamps for specific transitions
        Instant now = Instant.now();
        if (targetStatus == TicketStatus.ACKNOWLEDGED) {
            ticket.setAcknowledgedAt(now);
        } else if (targetStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(now);
        }

        Ticket savedTicket = ticketRepository.save(ticket);

        // Record status history
        StatusHistory history = StatusHistory.builder()
                .ticketId(ticketId)
                .previousStatus(previousStatus)
                .newStatus(targetStatus)
                .changedBy(actingUserId)
                .reason(reason)
                .changedAt(now)
                .build();
        statusHistoryRepository.save(history);

        // Publish domain event
        eventPublisher.publish(new StatusChangedEvent(
                actingUserId,
                ticket.getPropertyId(),
                ticketId,
                previousStatus.name(),
                targetStatus.name(),
                reason
        ));

        log.info("Ticket {} transitioned from {} to {} by user {}",
                ticketId, previousStatus, targetStatus, actingUserId);

        return savedTicket;
    }

    /**
     * Determines whether a reason is required for the given target status.
     * Reasons are mandatory for REJECTED, CANCELLED, and REOPENED transitions.
     */
    private boolean requiresReason(TicketStatus targetStatus) {
        return targetStatus == TicketStatus.REJECTED
                || targetStatus == TicketStatus.CANCELLED
                || targetStatus == TicketStatus.REOPENED;
    }

    /**
     * Reopens a ticket that was previously closed or resolved.
     *
     * <p>Performs the following:
     * <ol>
     *   <li>Validates the ticket exists and is in CLOSED or RESOLVED status</li>
     *   <li>Checks the reopen time window — the ticket must have been closed/resolved
     *       within the configurable window (default 72 hours)</li>
     *   <li>Validates the transition using the StatusWorkflowEngine</li>
     *   <li>Transitions the ticket to REOPENED status</li>
     *   <li>Records the reopen in StatusHistory with the provided reason</li>
     *   <li>Publishes a StatusChangedEvent</li>
     * </ol>
     *
     * @param ticketId     the ticket to reopen
     * @param request      the reopen request containing the reason
     * @param actingUserId the UUID of the user performing the reopen
     * @return the updated Ticket entity
     * @throws ResourceNotFoundException if the ticket is not found
     * @throws BusinessRuleViolationException if the reopen time window has expired
     *         or the ticket is not in a reopenable status
     */
    public Ticket reopenTicket(UUID ticketId, ReopenTicketRequest request, UUID actingUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        TicketStatus previousStatus = ticket.getStatus();

        // Validate that the ticket is in a reopenable status (CLOSED or RESOLVED)
        if (previousStatus != TicketStatus.CLOSED && previousStatus != TicketStatus.RESOLVED) {
            throw new BusinessRuleViolationException(
                    "Ticket can only be reopened from CLOSED or RESOLVED status. Current status: " + previousStatus.name());
        }

        // Validate the transition against the workflow policy
        statusWorkflowEngine.validateTransition(previousStatus, TicketStatus.REOPENED);

        // Check the reopen time window
        Instant closureTime = findClosureTime(ticketId, previousStatus);
        Duration reopenWindow = Duration.ofHours(ticketProperties.reopenWindowHours());
        Instant deadline = closureTime.plus(reopenWindow);

        if (Instant.now().isAfter(deadline)) {
            throw new BusinessRuleViolationException(
                    String.format("The reopen window of %d hours has expired. " +
                            "This ticket was %s on %s. Please submit a new ticket instead.",
                            ticketProperties.reopenWindowHours(),
                            previousStatus.name().toLowerCase(),
                            closureTime.toString()));
        }

        // Apply the status change
        ticket.setStatus(TicketStatus.REOPENED);
        Ticket savedTicket = ticketRepository.save(ticket);

        // Record status history
        Instant now = Instant.now();
        StatusHistory history = StatusHistory.builder()
                .ticketId(ticketId)
                .previousStatus(previousStatus)
                .newStatus(TicketStatus.REOPENED)
                .changedBy(actingUserId)
                .reason(request.reason())
                .changedAt(now)
                .build();
        statusHistoryRepository.save(history);

        // Publish domain event
        eventPublisher.publish(new StatusChangedEvent(
                actingUserId,
                ticket.getPropertyId(),
                ticketId,
                previousStatus.name(),
                TicketStatus.REOPENED.name(),
                request.reason()
        ));

        log.info("Ticket {} reopened from {} by user {}. Reason: {}",
                ticketId, previousStatus, actingUserId, request.reason());

        return savedTicket;
    }

    /**
     * Changes the category of a ticket.
     *
     * <p>Performs the following:
     * <ol>
     *   <li>Validates the ticket exists</li>
     *   <li>Checks that the new category is different from the current one</li>
     *   <li>Updates the category</li>
     *   <li>Recalculates SLA targets based on the new classification</li>
     *   <li>Records the change in StatusHistory</li>
     * </ol>
     *
     * @param ticketId      the ticket to update
     * @param newCategory   the new category
     * @param actingUserId  the UUID of the Property Manager performing the change
     * @return the updated Ticket entity
     * @throws ResourceNotFoundException if the ticket is not found
     * @throws BusinessRuleViolationException if the new category is the same as the current one
     */
    public Ticket changeCategory(UUID ticketId, Category newCategory, UUID actingUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        Category previousCategory = ticket.getCategory();

        if (previousCategory == newCategory) {
            throw new BusinessRuleViolationException(
                    "Ticket category is already " + newCategory.name() + ". No change required.");
        }

        // Update category
        ticket.setCategory(newCategory);

        // Recalculate SLA targets
        Property property = propertyRepository.findById(ticket.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property", ticket.getPropertyId()));
        SlaCalculator.SlaTargets slaTargets = slaCalculator.calculateTargets(
                ticket.getPropertyId(), property.getTimezone(), newCategory, ticket.getPriority());
        ticket.setAcknowledgementDueAt(slaTargets.acknowledgementDueAt());
        ticket.setResolutionDueAt(slaTargets.resolutionDueAt());

        Ticket savedTicket = ticketRepository.save(ticket);

        // Record in status history
        Instant now = Instant.now();
        StatusHistory history = StatusHistory.builder()
                .ticketId(ticketId)
                .previousStatus(ticket.getStatus())
                .newStatus(ticket.getStatus())
                .changedBy(actingUserId)
                .reason("Category changed from " + previousCategory.name() + " to " + newCategory.name())
                .changedAt(now)
                .build();
        statusHistoryRepository.save(history);

        log.info("Ticket {} category changed from {} to {} by user {}",
                ticketId, previousCategory, newCategory, actingUserId);

        return savedTicket;
    }

    /**
     * Changes the priority of a ticket.
     *
     * <p>Performs the following:
     * <ol>
     *   <li>Validates the ticket exists</li>
     *   <li>Checks that the new priority is different from the current one</li>
     *   <li>Updates the priority</li>
     *   <li>Recalculates SLA targets based on the new classification</li>
     *   <li>Records the change in StatusHistory with previous and new values</li>
     *   <li>Publishes a PriorityChangedEvent for SLA recalculation and audit</li>
     * </ol>
     *
     * @param ticketId      the ticket to update
     * @param newPriority   the new priority
     * @param actingUserId  the UUID of the Property Manager performing the change
     * @return the updated Ticket entity
     * @throws ResourceNotFoundException if the ticket is not found
     * @throws BusinessRuleViolationException if the new priority is the same as the current one
     */
    public Ticket changePriority(UUID ticketId, Priority newPriority, UUID actingUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        Priority previousPriority = ticket.getPriority();

        if (previousPriority == newPriority) {
            throw new BusinessRuleViolationException(
                    "Ticket priority is already " + newPriority.name() + ". No change required.");
        }

        // Update priority
        ticket.setPriority(newPriority);

        // Recalculate SLA targets
        Property property = propertyRepository.findById(ticket.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property", ticket.getPropertyId()));
        SlaCalculator.SlaTargets slaTargets = slaCalculator.calculateTargets(
                ticket.getPropertyId(), property.getTimezone(), ticket.getCategory(), newPriority);
        ticket.setAcknowledgementDueAt(slaTargets.acknowledgementDueAt());
        ticket.setResolutionDueAt(slaTargets.resolutionDueAt());

        Ticket savedTicket = ticketRepository.save(ticket);

        // Record in status history
        Instant now = Instant.now();
        StatusHistory history = StatusHistory.builder()
                .ticketId(ticketId)
                .previousStatus(ticket.getStatus())
                .newStatus(ticket.getStatus())
                .changedBy(actingUserId)
                .reason("Priority changed from " + previousPriority.name() + " to " + newPriority.name())
                .changedAt(now)
                .build();
        statusHistoryRepository.save(history);

        // Publish domain event for SLA recalculation and audit
        eventPublisher.publish(new PriorityChangedEvent(
                actingUserId,
                ticket.getPropertyId(),
                ticketId,
                previousPriority.name(),
                newPriority.name()
        ));

        log.info("Ticket {} priority changed from {} to {} by user {}",
                ticketId, previousPriority, newPriority, actingUserId);

        return savedTicket;
    }

    /**
     * Finds the time when the ticket was moved to its current closure/resolution status.
     * Uses the most recent status history entry that transitioned to the given status.
     */
    private Instant findClosureTime(UUID ticketId, TicketStatus currentStatus) {
        List<StatusHistory> history = statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId);

        // Find the most recent transition TO the current status
        return history.stream()
                .filter(h -> h.getNewStatus() == currentStatus)
                .reduce((first, second) -> second) // get the last one
                .map(StatusHistory::getChangedAt)
                .orElse(Instant.now()); // fallback to now if no history found (shouldn't happen)
    }

    /**
     * Enforces rate limiting on ticket submissions per resident.
     *
     * <p>Counts the number of tickets submitted by the resident within the configured
     * time period and rejects the submission if the limit is exceeded.
     *
     * @param submittedBy the UUID of the submitting resident
     * @throws RateLimitExceededException if the resident has exceeded the configured maximum
     *         submissions per time period
     */
    private void enforceSubmissionRateLimit(UUID submittedBy) {
        int maxSubmissions = ticketProperties.rateLimit().maxSubmissionsPerPeriod();
        int periodMinutes = ticketProperties.rateLimit().periodMinutes();

        Instant windowStart = Instant.now().minus(Duration.ofMinutes(periodMinutes));
        long recentSubmissions = ticketRepository.countBySubmittedByAndCreatedAtAfter(submittedBy, windowStart);

        if (recentSubmissions >= maxSubmissions) {
            long minutesRemaining = calculateMinutesRemaining(submittedBy, periodMinutes);
            throw new RateLimitExceededException(
                    String.format("You have exceeded the maximum of %d ticket submissions per %d minutes. " +
                            "Please wait approximately %d minute(s) before submitting again.",
                            maxSubmissions, periodMinutes, minutesRemaining));
        }
    }

    /**
     * Calculates the approximate number of minutes the resident must wait before
     * they can submit again. This is an estimate based on the configured period.
     */
    private long calculateMinutesRemaining(UUID submittedBy, int periodMinutes) {
        // Conservative estimate: user must wait for the full period to expire
        // A more precise calculation would look at the oldest submission in the window,
        // but this provides a reasonable estimate.
        return Math.max(1, periodMinutes);
    }

    /**
     * Finds a ticket by its ID.
     *
     * @param ticketId the ticket ID
     * @return the ticket
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Ticket findById(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    /**
     * Finds a ticket by its reference number.
     *
     * @param referenceNumber the reference number
     * @return the ticket
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Ticket findByReferenceNumber(String referenceNumber) {
        return ticketRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket with reference " + referenceNumber + " not found"));
    }

    /**
     * Lists all tickets for a property.
     *
     * @param propertyId the property ID
     * @return list of tickets
     */
    @Transactional(readOnly = true)
    public List<Ticket> findByPropertyId(UUID propertyId) {
        return ticketRepository.findByPropertyId(propertyId);
    }

    /**
     * Lists tickets visible to a resident within their property context.
     * Returns only tickets submitted by the resident OR related to the resident's linked unit(s).
     * This enforces the resident data scope per Requirement 21.1.
     *
     * @param propertyId the property context
     * @param residentId the resident's user ID
     * @return list of tickets the resident is allowed to see
     */
    @Transactional(readOnly = true)
    public List<Ticket> findResidentTickets(UUID propertyId, UUID residentId) {
        List<Membership> activeMemberships = membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId);

        List<UUID> unitIds = activeMemberships.stream()
                .filter(m -> m.getUnitId() != null)
                .map(Membership::getUnitId)
                .distinct()
                .toList();

        if (unitIds.isEmpty()) {
            return ticketRepository.findByPropertyIdAndSubmittedBy(propertyId, residentId);
        }

        return ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(propertyId, residentId, unitIds);
    }
}
