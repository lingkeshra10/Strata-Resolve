package com.strataresolve.ticket.service;

import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.domain.CommentVisibility;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.dto.CreateCommentRequest;
import com.strataresolve.ticket.repository.CommentRepository;
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
 * Service responsible for managing comments on tickets.
 * Handles adding public comments and internal notes with role-based visibility restrictions.
 *
 * <p>Internal notes (INTERNAL visibility) can only be created by users with management/staff roles:
 * PROPERTY_MANAGER, TECHNICIAN, or VENDOR_TECHNICIAN.
 *
 * <p>When retrieving comments, internal notes are filtered out for resident users.
 */
@Service
@Transactional
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    /**
     * Roles that are allowed to create and view internal notes.
     */
    private static final Set<Role> INTERNAL_NOTE_ROLES = Set.of(
            Role.PROPERTY_MANAGER,
            Role.TECHNICIAN,
            Role.VENDOR_TECHNICIAN
    );

    /**
     * Roles that are considered resident roles (cannot see internal notes).
     */
    private static final Set<Role> RESIDENT_ROLES = Set.of(
            Role.RESIDENT_OWNER,
            Role.RESIDENT_TENANT
    );

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final MembershipRepository membershipRepository;

    public CommentService(CommentRepository commentRepository,
                          TicketRepository ticketRepository,
                          MembershipRepository membershipRepository) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Adds a comment to a ticket.
     *
     * <p>Validates:
     * <ul>
     *   <li>The ticket exists</li>
     *   <li>If visibility is INTERNAL, the user must have a management/staff role</li>
     *   <li>Residents can only create PUBLIC comments</li>
     * </ul>
     *
     * @param ticketId  the ticket to add the comment to
     * @param request   the comment creation request
     * @param authorId  the UUID of the user creating the comment
     * @return the persisted Comment entity
     * @throws ResourceNotFoundException if the ticket is not found
     * @throws AccessDeniedException if a resident attempts to create an internal note
     */
    public Comment addComment(UUID ticketId, CreateCommentRequest request, UUID authorId) {
        // Validate ticket exists
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        UUID propertyId = ticket.getPropertyId();

        // Validate internal note permissions
        if (request.visibility() == CommentVisibility.INTERNAL) {
            validateCanCreateInternalNote(authorId, propertyId);
        }

        // Build and persist the comment
        Comment comment = Comment.builder()
                .ticketId(ticketId)
                .authorId(authorId)
                .content(request.content())
                .visibility(request.visibility())
                .build();

        Comment savedComment = commentRepository.save(comment);

        log.info("Comment added to ticket {} by user {} with visibility {}",
                ticketId, authorId, request.visibility());

        return savedComment;
    }

    /**
     * Retrieves all comments for a ticket, filtering internal notes for resident users.
     *
     * <p>If the requesting user is a resident (RESIDENT_OWNER or RESIDENT_TENANT),
     * only PUBLIC comments are returned. Management and staff users see all comments.
     *
     * @param ticketId      the ticket to get comments for
     * @param requestingUserId the UUID of the user requesting the comments
     * @return list of comments visible to the requesting user, ordered chronologically
     * @throws ResourceNotFoundException if the ticket is not found
     */
    @Transactional(readOnly = true)
    public List<Comment> getCommentsForTicket(UUID ticketId, UUID requestingUserId) {
        // Validate ticket exists
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        UUID propertyId = ticket.getPropertyId();

        // Determine if the user is a resident
        if (isResident(requestingUserId, propertyId)) {
            // Residents only see PUBLIC comments
            return commentRepository.findByTicketIdAndVisibilityOrderByCreatedAtAsc(
                    ticketId, CommentVisibility.PUBLIC);
        }

        // Non-residents see all comments
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    /**
     * Validates that the user has a role that permits creating internal notes.
     *
     * @throws AccessDeniedException if the user does not have a permitted role
     */
    private void validateCanCreateInternalNote(UUID userId, UUID propertyId) {
        List<Membership> memberships = membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId);

        boolean hasInternalNoteRole = memberships.stream()
                .anyMatch(m -> INTERNAL_NOTE_ROLES.contains(m.getRole()));

        if (!hasInternalNoteRole) {
            throw new AccessDeniedException(
                    "Only Property Managers, Technicians, and Vendor Technicians can create internal notes");
        }
    }

    /**
     * Determines whether the user is a resident based on their active memberships.
     * A user is considered a resident if all their active roles for the property are resident roles.
     */
    private boolean isResident(UUID userId, UUID propertyId) {
        List<Membership> memberships = membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId);

        if (memberships.isEmpty()) {
            // No membership — treat as resident-level access (most restrictive)
            return true;
        }

        // User is a resident if ALL their roles are resident roles
        return memberships.stream()
                .allMatch(m -> RESIDENT_ROLES.contains(m.getRole()));
    }
}
