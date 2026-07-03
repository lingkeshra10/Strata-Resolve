package com.strataresolve.ticket.service;

import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.domain.CommentVisibility;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.CreateCommentRequest;
import com.strataresolve.ticket.repository.CommentRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService")
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private CommentService commentService;

    private UUID propertyId;
    private UUID ticketId;
    private UUID residentUserId;
    private UUID propertyManagerUserId;
    private UUID technicianUserId;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, ticketRepository, membershipRepository);

        propertyId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        residentUserId = UUID.randomUUID();
        propertyManagerUserId = UUID.randomUUID();
        technicianUserId = UUID.randomUUID();

        ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(residentUserId)
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Leaking pipe")
                .description("Water leaking from ceiling")
                .status(TicketStatus.SUBMITTED)
                .build();
        ticket.setPropertyId(propertyId);
    }

    @Nested
    @DisplayName("Adding Comments")
    class AddCommentTests {

        @Test
        @DisplayName("should add a public comment successfully")
        void shouldAddPublicCommentSuccessfully() {
            CreateCommentRequest request = new CreateCommentRequest(
                    "The leak is getting worse", CommentVisibility.PUBLIC);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
                Comment c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                return c;
            });

            Comment result = commentService.addComment(ticketId, request, residentUserId);

            assertThat(result).isNotNull();
            assertThat(result.getTicketId()).isEqualTo(ticketId);
            assertThat(result.getAuthorId()).isEqualTo(residentUserId);
            assertThat(result.getContent()).isEqualTo("The leak is getting worse");
            assertThat(result.getVisibility()).isEqualTo(CommentVisibility.PUBLIC);

            ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
            verify(commentRepository).save(captor.capture());
            Comment saved = captor.getValue();
            assertThat(saved.getTicketId()).isEqualTo(ticketId);
            assertThat(saved.getAuthorId()).isEqualTo(residentUserId);
            assertThat(saved.getVisibility()).isEqualTo(CommentVisibility.PUBLIC);
        }

        @Test
        @DisplayName("should add an internal note by a Property Manager")
        void shouldAddInternalNoteByPropertyManager() {
            CreateCommentRequest request = new CreateCommentRequest(
                    "Vendor has been notified, awaiting quote", CommentVisibility.INTERNAL);

            Membership pmMembership = Membership.builder()
                    .id(UUID.randomUUID())
                    .userId(propertyManagerUserId)
                    .role(Role.PROPERTY_MANAGER)
                    .isActive(true)
                    .effectiveFrom(LocalDate.now())
                    .build();
            pmMembership.setPropertyId(propertyId);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(membershipRepository.findActiveByUserIdAndPropertyId(propertyManagerUserId, propertyId))
                    .thenReturn(List.of(pmMembership));
            when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
                Comment c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                return c;
            });

            Comment result = commentService.addComment(ticketId, request, propertyManagerUserId);

            assertThat(result).isNotNull();
            assertThat(result.getAuthorId()).isEqualTo(propertyManagerUserId);
            assertThat(result.getVisibility()).isEqualTo(CommentVisibility.INTERNAL);
            assertThat(result.getContent()).isEqualTo("Vendor has been notified, awaiting quote");
        }

        @Test
        @DisplayName("should add an internal note by a Technician")
        void shouldAddInternalNoteByTechnician() {
            CreateCommentRequest request = new CreateCommentRequest(
                    "Parts ordered, ETA 2 days", CommentVisibility.INTERNAL);

            Membership techMembership = Membership.builder()
                    .id(UUID.randomUUID())
                    .userId(technicianUserId)
                    .role(Role.TECHNICIAN)
                    .isActive(true)
                    .effectiveFrom(LocalDate.now())
                    .build();
            techMembership.setPropertyId(propertyId);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(membershipRepository.findActiveByUserIdAndPropertyId(technicianUserId, propertyId))
                    .thenReturn(List.of(techMembership));
            when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
                Comment c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                return c;
            });

            Comment result = commentService.addComment(ticketId, request, technicianUserId);

            assertThat(result).isNotNull();
            assertThat(result.getVisibility()).isEqualTo(CommentVisibility.INTERNAL);
        }

        @Test
        @DisplayName("should reject an internal note from a Resident")
        void shouldRejectInternalNoteFromResident() {
            CreateCommentRequest request = new CreateCommentRequest(
                    "Trying to add an internal note", CommentVisibility.INTERNAL);

            Membership residentMembership = Membership.builder()
                    .id(UUID.randomUUID())
                    .userId(residentUserId)
                    .role(Role.RESIDENT_OWNER)
                    .isActive(true)
                    .effectiveFrom(LocalDate.now())
                    .build();
            residentMembership.setPropertyId(propertyId);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(membershipRepository.findActiveByUserIdAndPropertyId(residentUserId, propertyId))
                    .thenReturn(List.of(residentMembership));

            assertThatThrownBy(() -> commentService.addComment(ticketId, request, residentUserId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only Property Managers, Technicians, and Vendor Technicians");

            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject comment when ticket does not exist")
        void shouldRejectWhenTicketNotFound() {
            CreateCommentRequest request = new CreateCommentRequest(
                    "Comment on non-existent ticket", CommentVisibility.PUBLIC);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.addComment(ticketId, request, residentUserId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should record author and timestamp for every comment")
        void shouldRecordAuthorAndTimestamp() {
            CreateCommentRequest request = new CreateCommentRequest(
                    "A comment with metadata", CommentVisibility.PUBLIC);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
                Comment c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                return c;
            });

            Comment result = commentService.addComment(ticketId, request, residentUserId);

            assertThat(result.getAuthorId()).isEqualTo(residentUserId);
            assertThat(result.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Retrieving Comments")
    class GetCommentsTests {

        @Test
        @DisplayName("should filter internal notes when resident views comments")
        void shouldFilterInternalNotesForResident() {
            Membership residentMembership = Membership.builder()
                    .id(UUID.randomUUID())
                    .userId(residentUserId)
                    .role(Role.RESIDENT_OWNER)
                    .isActive(true)
                    .effectiveFrom(LocalDate.now())
                    .build();
            residentMembership.setPropertyId(propertyId);

            Comment publicComment = Comment.builder()
                    .id(UUID.randomUUID())
                    .ticketId(ticketId)
                    .authorId(propertyManagerUserId)
                    .content("Public update")
                    .visibility(CommentVisibility.PUBLIC)
                    .createdAt(Instant.now())
                    .build();

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(membershipRepository.findActiveByUserIdAndPropertyId(residentUserId, propertyId))
                    .thenReturn(List.of(residentMembership));
            when(commentRepository.findByTicketIdAndVisibilityOrderByCreatedAtAsc(ticketId, CommentVisibility.PUBLIC))
                    .thenReturn(List.of(publicComment));

            List<Comment> results = commentService.getCommentsForTicket(ticketId, residentUserId);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getVisibility()).isEqualTo(CommentVisibility.PUBLIC);

            // Verify we called the filtered query, not the full query
            verify(commentRepository).findByTicketIdAndVisibilityOrderByCreatedAtAsc(ticketId, CommentVisibility.PUBLIC);
            verify(commentRepository, never()).findByTicketIdOrderByCreatedAtAsc(ticketId);
        }

        @Test
        @DisplayName("should return all comments including internal for Property Manager")
        void shouldReturnAllCommentsForPropertyManager() {
            Membership pmMembership = Membership.builder()
                    .id(UUID.randomUUID())
                    .userId(propertyManagerUserId)
                    .role(Role.PROPERTY_MANAGER)
                    .isActive(true)
                    .effectiveFrom(LocalDate.now())
                    .build();
            pmMembership.setPropertyId(propertyId);

            Comment publicComment = Comment.builder()
                    .id(UUID.randomUUID())
                    .ticketId(ticketId)
                    .authorId(residentUserId)
                    .content("Public comment")
                    .visibility(CommentVisibility.PUBLIC)
                    .createdAt(Instant.now().minusSeconds(60))
                    .build();

            Comment internalComment = Comment.builder()
                    .id(UUID.randomUUID())
                    .ticketId(ticketId)
                    .authorId(propertyManagerUserId)
                    .content("Internal note")
                    .visibility(CommentVisibility.INTERNAL)
                    .createdAt(Instant.now())
                    .build();

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(membershipRepository.findActiveByUserIdAndPropertyId(propertyManagerUserId, propertyId))
                    .thenReturn(List.of(pmMembership));
            when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                    .thenReturn(List.of(publicComment, internalComment));

            List<Comment> results = commentService.getCommentsForTicket(ticketId, propertyManagerUserId);

            assertThat(results).hasSize(2);

            // Verify we called the unfiltered query
            verify(commentRepository).findByTicketIdOrderByCreatedAtAsc(ticketId);
            verify(commentRepository, never()).findByTicketIdAndVisibilityOrderByCreatedAtAsc(any(), any());
        }

        @Test
        @DisplayName("should reject when ticket does not exist")
        void shouldRejectWhenTicketNotFound() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.getCommentsForTicket(ticketId, residentUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
