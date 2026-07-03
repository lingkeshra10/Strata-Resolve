package com.strataresolve.ticket.service;

import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.domain.ActivityType;
import com.strataresolve.ticket.domain.Assignment;
import com.strataresolve.ticket.domain.AssignmentType;
import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.domain.CommentVisibility;
import com.strataresolve.ticket.domain.StatusHistory;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.ActivityEntry;
import com.strataresolve.ticket.repository.AssignmentRepository;
import com.strataresolve.ticket.repository.AttachmentRepository;
import com.strataresolve.ticket.repository.CommentRepository;
import com.strataresolve.ticket.repository.StatusHistoryRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityHistoryService - Activity History")
class ActivityHistoryServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private StatusHistoryRepository statusHistoryRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private ActivityHistoryService activityHistoryService;

    private UUID ticketId;
    private UUID propertyId;
    private UUID managerId;
    private UUID residentId;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        activityHistoryService = new ActivityHistoryService(
                ticketRepository, statusHistoryRepository, commentRepository,
                assignmentRepository, attachmentRepository, membershipRepository);

        ticketId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        residentId = UUID.randomUUID();

        ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(residentId)
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test Ticket")
                .description("Test description")
                .status(TicketStatus.IN_PROGRESS)
                .build();
        ticket.setPropertyId(propertyId);
    }

    @Test
    @DisplayName("should return activities in chronological order")
    void shouldReturnActivitiesInChronologicalOrder() {
        // Given
        Instant t1 = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant t2 = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant t3 = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant t4 = Instant.now();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(managerId, propertyId))
                .thenReturn(List.of(buildMembership(managerId, Role.PROPERTY_MANAGER)));

        // Status change at t2
        StatusHistory statusHistory = StatusHistory.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .previousStatus(TicketStatus.SUBMITTED)
                .newStatus(TicketStatus.ACKNOWLEDGED)
                .changedBy(managerId)
                .changedAt(t2)
                .build();
        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                .thenReturn(List.of(statusHistory));

        // Comment at t1 (earliest)
        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(residentId)
                .content("Initial comment")
                .visibility(CommentVisibility.PUBLIC)
                .createdAt(t1)
                .build();
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                .thenReturn(List.of(comment));

        // Assignment at t3
        Assignment assignment = Assignment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .assignedTo(UUID.randomUUID())
                .type(AssignmentType.TECHNICIAN)
                .assignedAt(t3)
                .build();
        when(assignmentRepository.findByTicketId(ticketId))
                .thenReturn(List.of(assignment));

        // Attachment at t4 (latest)
        Attachment attachment = Attachment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .uploadedBy(residentId)
                .originalFilename("photo.jpg")
                .contentType("image/jpeg")
                .fileSize(1024)
                .storageReference("ref-123")
                .uploadedAt(t4)
                .build();
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId))
                .thenReturn(List.of(attachment));

        // When
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, managerId);

        // Then
        assertThat(activities).hasSize(4);
        // Verify chronological order
        assertThat(activities.get(0).timestamp()).isEqualTo(t1);
        assertThat(activities.get(1).timestamp()).isEqualTo(t2);
        assertThat(activities.get(2).timestamp()).isEqualTo(t3);
        assertThat(activities.get(3).timestamp()).isEqualTo(t4);

        // Verify order matches expected types
        assertThat(activities.get(0).type()).isEqualTo(ActivityType.COMMENT);
        assertThat(activities.get(1).type()).isEqualTo(ActivityType.STATUS_CHANGE);
        assertThat(activities.get(2).type()).isEqualTo(ActivityType.ASSIGNMENT);
        assertThat(activities.get(3).type()).isEqualTo(ActivityType.ATTACHMENT_UPLOAD);
    }

    @Test
    @DisplayName("should include all activity types in the response")
    void shouldIncludeAllActivityTypes() {
        // Given
        Instant now = Instant.now();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(managerId, propertyId))
                .thenReturn(List.of(buildMembership(managerId, Role.PROPERTY_MANAGER)));

        StatusHistory statusHistory = StatusHistory.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .previousStatus(TicketStatus.SUBMITTED)
                .newStatus(TicketStatus.ACKNOWLEDGED)
                .changedBy(managerId)
                .changedAt(now)
                .build();
        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                .thenReturn(List.of(statusHistory));

        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(residentId)
                .content("A comment")
                .visibility(CommentVisibility.PUBLIC)
                .createdAt(now.plusSeconds(1))
                .build();
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                .thenReturn(List.of(comment));

        Assignment assignment = Assignment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .assignedTo(UUID.randomUUID())
                .type(AssignmentType.VENDOR)
                .assignedAt(now.plusSeconds(2))
                .build();
        when(assignmentRepository.findByTicketId(ticketId))
                .thenReturn(List.of(assignment));

        Attachment attachment = Attachment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .uploadedBy(residentId)
                .originalFilename("document.pdf")
                .contentType("application/pdf")
                .fileSize(2048)
                .storageReference("ref-456")
                .uploadedAt(now.plusSeconds(3))
                .build();
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId))
                .thenReturn(List.of(attachment));

        // When
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, managerId);

        // Then
        assertThat(activities).extracting(ActivityEntry::type)
                .containsExactlyInAnyOrder(
                        ActivityType.STATUS_CHANGE,
                        ActivityType.COMMENT,
                        ActivityType.ASSIGNMENT,
                        ActivityType.ATTACHMENT_UPLOAD
                );
    }

    @Test
    @DisplayName("every entry should have non-null author and timestamp")
    void everyEntryShouldHaveNonNullAuthorAndTimestamp() {
        // Given
        Instant now = Instant.now();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(managerId, propertyId))
                .thenReturn(List.of(buildMembership(managerId, Role.PROPERTY_MANAGER)));

        StatusHistory statusHistory = StatusHistory.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .previousStatus(TicketStatus.ACKNOWLEDGED)
                .newStatus(TicketStatus.ASSIGNED)
                .changedBy(managerId)
                .changedAt(now)
                .build();
        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                .thenReturn(List.of(statusHistory));

        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(residentId)
                .content("Comment text")
                .visibility(CommentVisibility.PUBLIC)
                .createdAt(now.plusSeconds(10))
                .build();
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                .thenReturn(List.of(comment));

        Assignment assignment = Assignment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .assignedTo(UUID.randomUUID())
                .type(AssignmentType.TECHNICIAN)
                .assignedAt(now.plusSeconds(20))
                .build();
        when(assignmentRepository.findByTicketId(ticketId))
                .thenReturn(List.of(assignment));

        Attachment attachment = Attachment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .uploadedBy(residentId)
                .originalFilename("evidence.png")
                .contentType("image/png")
                .fileSize(512)
                .storageReference("ref-789")
                .uploadedAt(now.plusSeconds(30))
                .build();
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId))
                .thenReturn(List.of(attachment));

        // When
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, managerId);

        // Then
        assertThat(activities).isNotEmpty();
        for (ActivityEntry entry : activities) {
            assertThat(entry.actorId()).isNotNull();
            assertThat(entry.timestamp()).isNotNull();
        }
    }

    @Test
    @DisplayName("should filter internal notes for resident users")
    void shouldFilterInternalNotesForResidents() {
        // Given
        Instant now = Instant.now();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        // User is a resident
        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(List.of(buildMembership(residentId, Role.RESIDENT_OWNER)));

        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                .thenReturn(List.of());

        // For residents, only PUBLIC comments are fetched
        Comment publicComment = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(residentId)
                .content("Public comment")
                .visibility(CommentVisibility.PUBLIC)
                .createdAt(now)
                .build();
        when(commentRepository.findByTicketIdAndVisibilityOrderByCreatedAtAsc(ticketId, CommentVisibility.PUBLIC))
                .thenReturn(List.of(publicComment));

        when(assignmentRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId)).thenReturn(List.of());

        // When
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, residentId);

        // Then
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).type()).isEqualTo(ActivityType.COMMENT);
        assertThat(activities.get(0).metadata().get("visibility")).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("should show internal notes for property managers")
    void shouldShowInternalNotesForPropertyManagers() {
        // Given
        Instant now = Instant.now();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(managerId, propertyId))
                .thenReturn(List.of(buildMembership(managerId, Role.PROPERTY_MANAGER)));

        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                .thenReturn(List.of());

        // Manager sees all comments including internal
        Comment publicComment = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(residentId)
                .content("Public comment")
                .visibility(CommentVisibility.PUBLIC)
                .createdAt(now)
                .build();
        Comment internalNote = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(managerId)
                .content("Internal note")
                .visibility(CommentVisibility.INTERNAL)
                .createdAt(now.plusSeconds(5))
                .build();
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                .thenReturn(List.of(publicComment, internalNote));

        when(assignmentRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId)).thenReturn(List.of());

        // When
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, managerId);

        // Then
        assertThat(activities).hasSize(2);
        assertThat(activities).extracting(entry -> entry.metadata().get("visibility"))
                .containsExactly("PUBLIC", "INTERNAL");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when ticket does not exist")
    void shouldThrowWhenTicketNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(ticketRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityHistoryService.getActivityHistory(nonExistentId, managerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should return empty list when ticket has no activity")
    void shouldReturnEmptyListWhenNoActivity() {
        // Given
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(managerId, propertyId))
                .thenReturn(List.of(buildMembership(managerId, Role.PROPERTY_MANAGER)));
        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId)).thenReturn(List.of());
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());
        when(assignmentRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId)).thenReturn(List.of());

        // When
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, managerId);

        // Then
        assertThat(activities).isEmpty();
    }

    @Test
    @DisplayName("should handle attachment repository failure gracefully")
    void shouldHandleAttachmentRepositoryFailureGracefully() {
        // Given
        Instant now = Instant.now();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(managerId, propertyId))
                .thenReturn(List.of(buildMembership(managerId, Role.PROPERTY_MANAGER)));

        StatusHistory statusHistory = StatusHistory.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .previousStatus(TicketStatus.SUBMITTED)
                .newStatus(TicketStatus.ACKNOWLEDGED)
                .changedBy(managerId)
                .changedAt(now)
                .build();
        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                .thenReturn(List.of(statusHistory));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());
        when(assignmentRepository.findByTicketId(ticketId)).thenReturn(List.of());

        // Simulate attachment repository failure
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId))
                .thenThrow(new RuntimeException("Table does not exist"));

        // When
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, managerId);

        // Then - still returns other activities
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).type()).isEqualTo(ActivityType.STATUS_CHANGE);
    }

    @Test
    @DisplayName("status change metadata should include previous and new status")
    void statusChangeMetadataShouldIncludeStatuses() {
        // Given
        Instant now = Instant.now();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(managerId, propertyId))
                .thenReturn(List.of(buildMembership(managerId, Role.PROPERTY_MANAGER)));

        StatusHistory statusHistory = StatusHistory.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .previousStatus(TicketStatus.SUBMITTED)
                .newStatus(TicketStatus.ACKNOWLEDGED)
                .changedBy(managerId)
                .reason("Reviewed and acknowledged")
                .changedAt(now)
                .build();
        when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                .thenReturn(List.of(statusHistory));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());
        when(assignmentRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId)).thenReturn(List.of());

        // When
        List<ActivityEntry> activities = activityHistoryService.getActivityHistory(ticketId, managerId);

        // Then
        assertThat(activities).hasSize(1);
        ActivityEntry entry = activities.get(0);
        assertThat(entry.metadata().get("previousStatus")).isEqualTo("SUBMITTED");
        assertThat(entry.metadata().get("newStatus")).isEqualTo("ACKNOWLEDGED");
        assertThat(entry.metadata().get("reason")).isEqualTo("Reviewed and acknowledged");
    }

    private Membership buildMembership(UUID userId, Role role) {
        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .role(role)
                .isActive(true)
                .build();
        membership.setPropertyId(propertyId);
        return membership;
    }
}
