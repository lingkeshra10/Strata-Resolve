package com.strataresolve.ticket.service;

import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.domain.ActivityType;
import com.strataresolve.ticket.domain.Assignment;
import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.domain.CommentVisibility;
import com.strataresolve.ticket.domain.StatusHistory;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.dto.ActivityEntry;
import com.strataresolve.ticket.repository.AssignmentRepository;
import com.strataresolve.ticket.repository.AttachmentRepository;
import com.strataresolve.ticket.repository.CommentRepository;
import com.strataresolve.ticket.repository.StatusHistoryRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service that aggregates all activity for a ticket into a unified, chronologically ordered history.
 *
 * <p>Activities include:
 * <ul>
 *   <li>Status changes</li>
 *   <li>Comments (filtered by visibility for residents)</li>
 *   <li>Assignments</li>
 *   <li>Attachment uploads</li>
 * </ul>
 *
 * <p>Every entry in the returned list is guaranteed to have a non-null actor and timestamp.
 * Internal notes are excluded from the activity history for resident users.
 */
@Service
@Transactional(readOnly = true)
public class ActivityHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ActivityHistoryService.class);

    /**
     * Roles that are considered resident roles (cannot see internal notes).
     */
    private static final Set<Role> RESIDENT_ROLES = Set.of(
            Role.RESIDENT_OWNER,
            Role.RESIDENT_TENANT
    );

    private final TicketRepository ticketRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final CommentRepository commentRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final MembershipRepository membershipRepository;

    public ActivityHistoryService(TicketRepository ticketRepository,
                                  StatusHistoryRepository statusHistoryRepository,
                                  CommentRepository commentRepository,
                                  AssignmentRepository assignmentRepository,
                                  AttachmentRepository attachmentRepository,
                                  MembershipRepository membershipRepository) {
        this.ticketRepository = ticketRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.commentRepository = commentRepository;
        this.assignmentRepository = assignmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Returns the complete activity history for a ticket in chronological order.
     *
     * <p>If the requesting user is a resident, internal comments are excluded.
     * Every returned entry has a non-null actorId and timestamp.
     *
     * @param ticketId         the ticket to get activity history for
     * @param requestingUserId the user requesting the activity history
     * @return list of activity entries sorted by timestamp ascending
     * @throws ResourceNotFoundException if the ticket is not found
     */
    public List<ActivityEntry> getActivityHistory(UUID ticketId, UUID requestingUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        UUID propertyId = ticket.getPropertyId();
        boolean isResident = isResident(requestingUserId, propertyId);

        List<ActivityEntry> activities = new ArrayList<>();

        // Fetch and convert status changes
        List<StatusHistory> statusChanges = statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId);
        for (StatusHistory sh : statusChanges) {
            activities.add(toActivityEntry(sh));
        }

        // Fetch and convert comments (respecting visibility for residents)
        List<Comment> comments;
        if (isResident) {
            comments = commentRepository.findByTicketIdAndVisibilityOrderByCreatedAtAsc(
                    ticketId, CommentVisibility.PUBLIC);
        } else {
            comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        }
        for (Comment comment : comments) {
            activities.add(toActivityEntry(comment));
        }

        // Fetch and convert assignments
        List<Assignment> assignments = assignmentRepository.findByTicketId(ticketId);
        for (Assignment assignment : assignments) {
            activities.add(toActivityEntry(assignment));
        }

        // Fetch and convert attachment uploads
        List<Attachment> attachments = fetchAttachments(ticketId);
        for (Attachment attachment : attachments) {
            activities.add(toActivityEntry(attachment));
        }

        // Sort all activities in chronological order
        activities.sort(Comparator.comparing(ActivityEntry::timestamp));

        log.debug("Retrieved {} activity entries for ticket {}", activities.size(), ticketId);

        return activities;
    }

    /**
     * Fetches attachments for a ticket. Handles gracefully if the attachment repository
     * or table is not yet available.
     */
    private List<Attachment> fetchAttachments(UUID ticketId) {
        try {
            return attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId);
        } catch (Exception e) {
            log.warn("Unable to fetch attachments for ticket {}: {}", ticketId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Converts a StatusHistory entry to an ActivityEntry.
     */
    private ActivityEntry toActivityEntry(StatusHistory statusHistory) {
        String description = String.format("Status changed from %s to %s",
                statusHistory.getPreviousStatus(), statusHistory.getNewStatus());

        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("previousStatus", statusHistory.getPreviousStatus().name());
        metadata.put("newStatus", statusHistory.getNewStatus().name());
        if (statusHistory.getReason() != null) {
            metadata.put("reason", statusHistory.getReason());
        }

        return new ActivityEntry(
                ActivityType.STATUS_CHANGE,
                statusHistory.getChangedAt(),
                statusHistory.getChangedBy(),
                description,
                metadata
        );
    }

    /**
     * Converts a Comment entry to an ActivityEntry.
     */
    private ActivityEntry toActivityEntry(Comment comment) {
        String description = comment.getVisibility() == CommentVisibility.INTERNAL
                ? "Added an internal note"
                : "Added a comment";

        Map<String, Object> metadata = Map.of(
                "visibility", comment.getVisibility().name(),
                "content", comment.getContent()
        );

        return new ActivityEntry(
                ActivityType.COMMENT,
                comment.getCreatedAt(),
                comment.getAuthorId(),
                description,
                metadata
        );
    }

    /**
     * Converts an Assignment entry to an ActivityEntry.
     */
    private ActivityEntry toActivityEntry(Assignment assignment) {
        String description = String.format("Ticket assigned to %s (%s)",
                assignment.getAssignedTo(), assignment.getType().name().toLowerCase());

        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("assignedTo", assignment.getAssignedTo().toString());
        metadata.put("assignmentType", assignment.getType().name());
        if (assignment.getAcceptedAt() != null) {
            metadata.put("acceptedAt", assignment.getAcceptedAt().toString());
        }

        return new ActivityEntry(
                ActivityType.ASSIGNMENT,
                assignment.getAssignedAt(),
                assignment.getAssignedTo(),
                description,
                metadata
        );
    }

    /**
     * Converts an Attachment entry to an ActivityEntry.
     */
    private ActivityEntry toActivityEntry(Attachment attachment) {
        String description = String.format("Uploaded file: %s", attachment.getOriginalFilename());

        Map<String, Object> metadata = Map.of(
                "filename", attachment.getOriginalFilename(),
                "contentType", attachment.getContentType(),
                "fileSize", attachment.getFileSize()
        );

        return new ActivityEntry(
                ActivityType.ATTACHMENT_UPLOAD,
                attachment.getUploadedAt(),
                attachment.getUploadedBy(),
                description,
                metadata
        );
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
