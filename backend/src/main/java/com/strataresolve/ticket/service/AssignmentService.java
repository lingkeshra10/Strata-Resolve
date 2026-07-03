package com.strataresolve.ticket.service;

import com.strataresolve.shared.event.AssignmentCreatedEvent;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.domain.Assignment;
import com.strataresolve.ticket.domain.AssignmentType;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.CreateAssignmentRequest;
import com.strataresolve.ticket.dto.TransitionTicketStatusRequest;
import com.strataresolve.ticket.repository.AssignmentRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service responsible for managing ticket assignments to technicians and vendors.
 *
 * <p>Validates that the assigned target has an active membership with the appropriate role
 * for the ticket's property, transitions the ticket status to ASSIGNED, creates the
 * assignment record, and publishes an AssignmentCreatedEvent.</p>
 */
@Service
@Transactional
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private static final Set<Role> TECHNICIAN_ROLES = Set.of(Role.TECHNICIAN);
    private static final Set<Role> VENDOR_ROLES = Set.of(Role.VENDOR_ADMIN, Role.VENDOR_TECHNICIAN);

    private final AssignmentRepository assignmentRepository;
    private final TicketRepository ticketRepository;
    private final MembershipRepository membershipRepository;
    private final TicketService ticketService;
    private final DomainEventPublisher eventPublisher;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             TicketRepository ticketRepository,
                             MembershipRepository membershipRepository,
                             TicketService ticketService,
                             DomainEventPublisher eventPublisher) {
        this.assignmentRepository = assignmentRepository;
        this.ticketRepository = ticketRepository;
        this.membershipRepository = membershipRepository;
        this.ticketService = ticketService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a ticket assignment.
     *
     * <p>Performs the following:
     * <ol>
     *   <li>Validates the ticket exists</li>
     *   <li>Validates the assigned target has an active membership for the ticket's property</li>
     *   <li>Validates the membership role matches the assignment type</li>
     *   <li>Transitions the ticket status to ASSIGNED</li>
     *   <li>Creates and persists the Assignment entity</li>
     *   <li>Publishes an AssignmentCreatedEvent</li>
     * </ol>
     *
     * @param request      the assignment creation request
     * @param actingUserId the UUID of the property manager performing the assignment
     * @return the created Assignment entity
     * @throws ResourceNotFoundException if the ticket is not found
     * @throws BusinessRuleViolationException if the assignee has no valid membership or wrong role
     */
    public Assignment createAssignment(CreateAssignmentRequest request, UUID actingUserId) {
        // 1. Validate ticket exists
        Ticket ticket = ticketRepository.findById(request.ticketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", request.ticketId()));

        UUID propertyId = ticket.getPropertyId();

        // 2. Validate assignee has active membership for the ticket's property
        List<Membership> activeMemberships = membershipRepository
                .findActiveByUserIdAndPropertyId(request.assignedTo(), propertyId);

        if (activeMemberships.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Assigned user does not have an active membership for the ticket's property");
        }

        // 3. Validate the membership role matches the assignment type
        validateAssigneeRole(activeMemberships, request.type(), request.assignedTo());

        // 4. Transition ticket status to ASSIGNED
        TransitionTicketStatusRequest transitionRequest = new TransitionTicketStatusRequest(
                TicketStatus.ASSIGNED, "Ticket assigned to " + request.type().name().toLowerCase());
        ticketService.transitionStatus(request.ticketId(), transitionRequest, actingUserId);

        // 5. Create and persist the assignment
        Assignment assignment = Assignment.builder()
                .ticketId(request.ticketId())
                .assignedTo(request.assignedTo())
                .type(request.type())
                .build();

        Assignment savedAssignment = assignmentRepository.save(assignment);

        // 6. Publish domain event
        eventPublisher.publish(new AssignmentCreatedEvent(
                actingUserId,
                propertyId,
                request.ticketId(),
                request.assignedTo(),
                request.type().name()
        ));

        log.info("Assignment created: ticket={}, assignedTo={}, type={}, by={}",
                request.ticketId(), request.assignedTo(), request.type(), actingUserId);

        return savedAssignment;
    }

    /**
     * Validates that the assignee has a membership role appropriate for the assignment type.
     *
     * <p>For TECHNICIAN assignments, the assignee must hold a TECHNICIAN role.
     * For VENDOR assignments, the assignee must hold a VENDOR_ADMIN or VENDOR_TECHNICIAN role.</p>
     *
     * @throws BusinessRuleViolationException if no matching role is found
     */
    private void validateAssigneeRole(List<Membership> memberships, AssignmentType type, UUID assigneeId) {
        Set<Role> requiredRoles = type == AssignmentType.TECHNICIAN ? TECHNICIAN_ROLES : VENDOR_ROLES;

        boolean hasMatchingRole = memberships.stream()
                .anyMatch(m -> requiredRoles.contains(m.getRole()));

        if (!hasMatchingRole) {
            String expectedRoles = type == AssignmentType.TECHNICIAN
                    ? "TECHNICIAN"
                    : "VENDOR_ADMIN or VENDOR_TECHNICIAN";
            throw new BusinessRuleViolationException(
                    String.format("Assigned user %s does not have the required role (%s) for a %s assignment",
                            assigneeId, expectedRoles, type.name()));
        }
    }

    /**
     * Finds all assignments for a given ticket.
     *
     * @param ticketId the ticket ID
     * @return list of assignments
     */
    @Transactional(readOnly = true)
    public List<Assignment> findByTicketId(UUID ticketId) {
        return assignmentRepository.findByTicketId(ticketId);
    }

    /**
     * Finds all assignments for a given assignee.
     *
     * @param assignedTo the assignee's user ID
     * @return list of assignments
     */
    @Transactional(readOnly = true)
    public List<Assignment> findByAssignedTo(UUID assignedTo) {
        return assignmentRepository.findByAssignedTo(assignedTo);
    }
}
