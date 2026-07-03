package com.strataresolve.property;

import com.strataresolve.ticket.domain.ActivityType;
import com.strataresolve.ticket.domain.Assignment;
import com.strataresolve.ticket.domain.AssignmentType;
import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.domain.CommentVisibility;
import com.strataresolve.ticket.domain.StatusHistory;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.dto.ActivityEntry;
import com.strataresolve.ticket.repository.AssignmentRepository;
import com.strataresolve.ticket.repository.AttachmentRepository;
import com.strataresolve.ticket.repository.CommentRepository;
import com.strataresolve.ticket.repository.StatusHistoryRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.ticket.service.ActivityHistoryService;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Activity History Completeness and Order.
 *
 * <p><b>Property 12: Activity History Completeness and Order</b></p>
 * <p><b>Validates: Requirements 10.3, 10.4</b></p>
 *
 * <p>For any ticket with multiple activities, the complete history SHALL be returned
 * in chronological order, and every entry SHALL contain a non-null author and timestamp.</p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 12: Activity History Completeness and Order")
class ActivityHistoryPropertyTest {

    private static final Instant BASE_TIME = Instant.parse("2025-01-01T00:00:00Z");

    /**
     * Property: For any combination of activities on a ticket, the returned history
     * is in strict chronological (non-decreasing timestamp) order.
     *
     * Validates: Requirements 10.3
     */
    @Property(tries = 150)
    void activityHistoryIsReturnedInChronologicalOrder(
            @ForAll("statusHistories") List<StatusHistory> statusHistories,
            @ForAll("comments") List<Comment> comments,
            @ForAll("assignments") List<Assignment> assignments,
            @ForAll("attachments") List<Attachment> attachments
    ) {
        // Setup
        UUID ticketId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();

        Ticket ticket = buildTicket(ticketId, propertyId);
        setTicketIdOnAll(ticketId, statusHistories, comments, assignments, attachments);

        ActivityHistoryService service = buildServiceWithMocks(
                ticket, ticketId, propertyId, requestingUserId,
                statusHistories, comments, assignments, attachments,
                Role.PROPERTY_MANAGER
        );

        // Act
        List<ActivityEntry> activities = service.getActivityHistory(ticketId, requestingUserId);

        // Assert: chronological order (each timestamp >= previous)
        for (int i = 1; i < activities.size(); i++) {
            assertThat(activities.get(i).timestamp())
                    .as("Activity at index %d should be >= activity at index %d", i, i - 1)
                    .isAfterOrEqualTo(activities.get(i - 1).timestamp());
        }
    }

    /**
     * Property: For any combination of activities on a ticket, every entry in the
     * returned history has a non-null actorId and a non-null timestamp.
     *
     * Validates: Requirements 10.4
     */
    @Property(tries = 150)
    void everyActivityEntryHasNonNullAuthorAndTimestamp(
            @ForAll("statusHistories") List<StatusHistory> statusHistories,
            @ForAll("comments") List<Comment> comments,
            @ForAll("assignments") List<Assignment> assignments,
            @ForAll("attachments") List<Attachment> attachments
    ) {
        // Setup
        UUID ticketId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();

        Ticket ticket = buildTicket(ticketId, propertyId);
        setTicketIdOnAll(ticketId, statusHistories, comments, assignments, attachments);

        ActivityHistoryService service = buildServiceWithMocks(
                ticket, ticketId, propertyId, requestingUserId,
                statusHistories, comments, assignments, attachments,
                Role.PROPERTY_MANAGER
        );

        // Act
        List<ActivityEntry> activities = service.getActivityHistory(ticketId, requestingUserId);

        // Assert: every entry has non-null actorId and timestamp
        for (ActivityEntry entry : activities) {
            assertThat(entry.actorId())
                    .as("Every activity entry must have a non-null author (actorId)")
                    .isNotNull();
            assertThat(entry.timestamp())
                    .as("Every activity entry must have a non-null timestamp")
                    .isNotNull();
        }
    }

    /**
     * Property: For any combination of activities on a ticket, the returned history
     * contains all activity types present in the input (completeness). The total count
     * of returned entries equals the sum of all input activity items.
     *
     * Validates: Requirements 10.3
     */
    @Property(tries = 150)
    void activityHistoryIsComplete(
            @ForAll("statusHistories") List<StatusHistory> statusHistories,
            @ForAll("comments") List<Comment> comments,
            @ForAll("assignments") List<Assignment> assignments,
            @ForAll("attachments") List<Attachment> attachments
    ) {
        // Setup
        UUID ticketId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();

        Ticket ticket = buildTicket(ticketId, propertyId);
        setTicketIdOnAll(ticketId, statusHistories, comments, assignments, attachments);

        ActivityHistoryService service = buildServiceWithMocks(
                ticket, ticketId, propertyId, requestingUserId,
                statusHistories, comments, assignments, attachments,
                Role.PROPERTY_MANAGER
        );

        // Act
        List<ActivityEntry> activities = service.getActivityHistory(ticketId, requestingUserId);

        // Assert: total count matches sum of all input activities
        int expectedTotal = statusHistories.size() + comments.size()
                + assignments.size() + attachments.size();
        assertThat(activities).hasSize(expectedTotal);

        // Assert: correct count per activity type
        long statusChangeCount = activities.stream()
                .filter(a -> a.type() == ActivityType.STATUS_CHANGE).count();
        long commentCount = activities.stream()
                .filter(a -> a.type() == ActivityType.COMMENT).count();
        long assignmentCount = activities.stream()
                .filter(a -> a.type() == ActivityType.ASSIGNMENT).count();
        long attachmentCount = activities.stream()
                .filter(a -> a.type() == ActivityType.ATTACHMENT_UPLOAD).count();

        assertThat(statusChangeCount).isEqualTo(statusHistories.size());
        assertThat(commentCount).isEqualTo(comments.size());
        assertThat(assignmentCount).isEqualTo(assignments.size());
        assertThat(attachmentCount).isEqualTo(attachments.size());
    }

    // ======================== Generators ========================

    @Provide
    Arbitrary<List<StatusHistory>> statusHistories() {
        return statusHistoryArbitrary().list().ofMinSize(0).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<Comment>> comments() {
        return commentArbitrary().list().ofMinSize(0).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<Assignment>> assignments() {
        return assignmentArbitrary().list().ofMinSize(0).ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<Attachment>> attachments() {
        return attachmentArbitrary().list().ofMinSize(0).ofMaxSize(5);
    }

    private Arbitrary<StatusHistory> statusHistoryArbitrary() {
        Arbitrary<TicketStatus> previousStatus = Arbitraries.of(TicketStatus.values());
        Arbitrary<TicketStatus> newStatus = Arbitraries.of(TicketStatus.values());
        Arbitrary<Instant> timestamp = instantArbitrary();
        Arbitrary<UUID> actor = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> reason = Arbitraries.of("Reviewed", "Acknowledged", "Assigned to team", null);

        return Combinators.combine(previousStatus, newStatus, timestamp, actor, reason)
                .as((prev, next, ts, actorId, r) -> StatusHistory.builder()
                        .id(UUID.randomUUID())
                        .ticketId(UUID.randomUUID()) // will be overridden
                        .previousStatus(prev)
                        .newStatus(next)
                        .changedBy(actorId)
                        .changedAt(ts)
                        .reason(r)
                        .build());
    }

    private Arbitrary<Comment> commentArbitrary() {
        Arbitrary<Instant> timestamp = instantArbitrary();
        Arbitrary<UUID> author = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> content = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(100);
        Arbitrary<CommentVisibility> visibility = Arbitraries.of(CommentVisibility.PUBLIC, CommentVisibility.INTERNAL);

        return Combinators.combine(timestamp, author, content, visibility)
                .as((ts, authorId, c, vis) -> Comment.builder()
                        .id(UUID.randomUUID())
                        .ticketId(UUID.randomUUID()) // will be overridden
                        .authorId(authorId)
                        .content(c)
                        .visibility(vis)
                        .createdAt(ts)
                        .build());
    }

    private Arbitrary<Assignment> assignmentArbitrary() {
        Arbitrary<Instant> timestamp = instantArbitrary();
        Arbitrary<UUID> assignee = Arbitraries.create(UUID::randomUUID);
        Arbitrary<AssignmentType> type = Arbitraries.of(AssignmentType.values());

        return Combinators.combine(timestamp, assignee, type)
                .as((ts, assignedTo, t) -> Assignment.builder()
                        .id(UUID.randomUUID())
                        .ticketId(UUID.randomUUID()) // will be overridden
                        .assignedTo(assignedTo)
                        .type(t)
                        .assignedAt(ts)
                        .build());
    }

    private Arbitrary<Attachment> attachmentArbitrary() {
        Arbitrary<Instant> timestamp = instantArbitrary();
        Arbitrary<UUID> uploader = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> filename = Arbitraries.of("photo.jpg", "evidence.png", "report.pdf", "scan.jpeg");
        Arbitrary<String> contentType = Arbitraries.of("image/jpeg", "image/png", "application/pdf");
        Arbitrary<Long> fileSize = Arbitraries.longs().between(100L, 10_000_000L);

        return Combinators.combine(timestamp, uploader, filename, contentType, fileSize)
                .as((ts, uploadedBy, fn, ct, size) -> Attachment.builder()
                        .id(UUID.randomUUID())
                        .ticketId(UUID.randomUUID()) // will be overridden
                        .uploadedBy(uploadedBy)
                        .originalFilename(fn)
                        .contentType(ct)
                        .fileSize(size)
                        .storageReference("storage/" + UUID.randomUUID())
                        .uploadedAt(ts)
                        .build());
    }

    /**
     * Generates timestamps distributed over a 30-day window from the base time.
     */
    private Arbitrary<Instant> instantArbitrary() {
        return Arbitraries.longs().between(0L, 30L * 24 * 3600)
                .map(offsetSeconds -> BASE_TIME.plusSeconds(offsetSeconds));
    }

    // ======================== Helper Methods ========================

    private Ticket buildTicket(UUID ticketId, UUID propertyId) {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test Ticket")
                .description("Property test ticket")
                .category(Category.PLUMBING)
                .priority(Priority.NORMAL)
                .status(TicketStatus.IN_PROGRESS)
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }

    private void setTicketIdOnAll(UUID ticketId,
                                  List<StatusHistory> statusHistories,
                                  List<Comment> comments,
                                  List<Assignment> assignments,
                                  List<Attachment> attachments) {
        statusHistories.forEach(sh -> sh.setTicketId(ticketId));
        comments.forEach(c -> c.setTicketId(ticketId));
        assignments.forEach(a -> a.setTicketId(ticketId));
        attachments.forEach(a -> a.setTicketId(ticketId));
    }

    private ActivityHistoryService buildServiceWithMocks(
            Ticket ticket, UUID ticketId, UUID propertyId, UUID requestingUserId,
            List<StatusHistory> statusHistories, List<Comment> comments,
            List<Assignment> assignments, List<Attachment> attachments,
            Role userRole) {

        TicketRepository ticketRepository = mock(TicketRepository.class);
        StatusHistoryRepository statusHistoryRepository = mock(StatusHistoryRepository.class);
        CommentRepository commentRepository = mock(CommentRepository.class);
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(requestingUserId)
                .role(userRole)
                .isActive(true)
                .build();
        membership.setPropertyId(propertyId);
        when(membershipRepository.findActiveByUserIdAndPropertyId(requestingUserId, propertyId))
                .thenReturn(List.of(membership));

        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                .thenReturn(statusHistories);

        // For non-resident users, all comments are returned
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                .thenReturn(comments);

        when(assignmentRepository.findByTicketId(ticketId))
                .thenReturn(assignments);

        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId))
                .thenReturn(attachments);

        return new ActivityHistoryService(
                ticketRepository, statusHistoryRepository, commentRepository,
                assignmentRepository, attachmentRepository, membershipRepository
        );
    }
}
