package com.strataresolve.property;

import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.domain.CommentVisibility;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.CreateCommentRequest;
import com.strataresolve.ticket.repository.CommentRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.ticket.service.CommentService;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Internal Note Visibility Restriction.
 *
 * <p><b>Property 11: Internal Note Visibility Restriction</b></p>
 * <p>For any internal note added to a ticket, it SHALL be visible to property managers,
 * technicians, and vendor technicians assigned to the ticket, but SHALL NOT be visible to residents.</p>
 *
 * <p><b>Validates: Requirements 10.2</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 11: Internal Note Visibility Restriction")
class InternalNoteVisibilityPropertyTest {

    private static final java.util.Set<Role> RESIDENT_ROLES = java.util.Set.of(
            Role.RESIDENT_OWNER,
            Role.RESIDENT_TENANT
    );

    private static final java.util.Set<Role> INTERNAL_NOTE_ALLOWED_ROLES = java.util.Set.of(
            Role.PROPERTY_MANAGER,
            Role.TECHNICIAN,
            Role.VENDOR_TECHNICIAN
    );

    // =====================================================================
    // Property: Internal notes SHALL NOT be visible to residents
    // =====================================================================

    /**
     * For any resident role and any internal note on a ticket, when the resident
     * retrieves comments, the internal note SHALL NOT be included in the results.
     *
     * <p><b>Validates: Requirements 10.2</b></p>
     */
    @Property(tries = 100)
    void internalNotesShouldNeverBeVisibleToResidents(
            @ForAll("residentRoles") Role residentRole,
            @ForAll("noteContents") String noteContent
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID residentUserId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();

        CommentRepository commentRepository = mock(CommentRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);

        CommentService commentService = new CommentService(commentRepository, ticketRepository, membershipRepository);

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(residentUserId)
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test ticket")
                .description("Test description")
                .status(TicketStatus.SUBMITTED)
                .build();
        ticket.setPropertyId(propertyId);

        Membership residentMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentUserId)
                .role(residentRole)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        residentMembership.setPropertyId(propertyId);

        // Only public comments should be returned for residents
        Comment publicComment = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(staffUserId)
                .content("Public update")
                .visibility(CommentVisibility.PUBLIC)
                .createdAt(Instant.now())
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(residentUserId, propertyId))
                .thenReturn(List.of(residentMembership));
        when(commentRepository.findByTicketIdAndVisibilityOrderByCreatedAtAsc(ticketId, CommentVisibility.PUBLIC))
                .thenReturn(List.of(publicComment));

        // Act
        List<Comment> results = commentService.getCommentsForTicket(ticketId, residentUserId);

        // Assert: no internal notes should be in the results
        assertThat(results)
                .allSatisfy(comment ->
                        assertThat(comment.getVisibility()).isEqualTo(CommentVisibility.PUBLIC));

        // Verify the filtered query was used (not the unfiltered one)
        verify(commentRepository).findByTicketIdAndVisibilityOrderByCreatedAtAsc(ticketId, CommentVisibility.PUBLIC);
        verify(commentRepository, never()).findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    // =====================================================================
    // Property: Internal notes SHALL be visible to management/staff roles
    // =====================================================================

    /**
     * For any management/staff role (PROPERTY_MANAGER, TECHNICIAN, VENDOR_TECHNICIAN),
     * when they retrieve comments for a ticket containing internal notes, all comments
     * (including internal notes) SHALL be returned.
     *
     * <p><b>Validates: Requirements 10.2</b></p>
     */
    @Property(tries = 100)
    void internalNotesShouldBeVisibleToManagementAndStaff(
            @ForAll("internalNoteAllowedRoles") Role staffRole,
            @ForAll("noteContents") String noteContent
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        UUID residentUserId = UUID.randomUUID();

        CommentRepository commentRepository = mock(CommentRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);

        CommentService commentService = new CommentService(commentRepository, ticketRepository, membershipRepository);

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(residentUserId)
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000002")
                .title("Test ticket")
                .description("Test description")
                .status(TicketStatus.IN_PROGRESS)
                .build();
        ticket.setPropertyId(propertyId);

        Membership staffMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(staffUserId)
                .role(staffRole)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        staffMembership.setPropertyId(propertyId);

        Comment publicComment = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(residentUserId)
                .content("Public comment from resident")
                .visibility(CommentVisibility.PUBLIC)
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        Comment internalComment = Comment.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .authorId(staffUserId)
                .content(noteContent)
                .visibility(CommentVisibility.INTERNAL)
                .createdAt(Instant.now())
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(staffUserId, propertyId))
                .thenReturn(List.of(staffMembership));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                .thenReturn(List.of(publicComment, internalComment));

        // Act
        List<Comment> results = commentService.getCommentsForTicket(ticketId, staffUserId);

        // Assert: both public and internal comments should be visible
        assertThat(results).hasSize(2);
        assertThat(results).anyMatch(c -> c.getVisibility() == CommentVisibility.INTERNAL);
        assertThat(results).anyMatch(c -> c.getVisibility() == CommentVisibility.PUBLIC);

        // Verify the unfiltered query was used
        verify(commentRepository).findByTicketIdOrderByCreatedAtAsc(ticketId);
        verify(commentRepository, never()).findByTicketIdAndVisibilityOrderByCreatedAtAsc(any(), any());
    }

    // =====================================================================
    // Property: Residents SHALL NOT be able to create internal notes
    // =====================================================================

    /**
     * For any resident role attempting to create an internal note, the system SHALL
     * reject the operation with an AccessDeniedException.
     *
     * <p><b>Validates: Requirements 10.2</b></p>
     */
    @Property(tries = 100)
    void residentsShouldNotBeAbleToCreateInternalNotes(
            @ForAll("residentRoles") Role residentRole,
            @ForAll("noteContents") String noteContent
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID residentUserId = UUID.randomUUID();

        CommentRepository commentRepository = mock(CommentRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);

        CommentService commentService = new CommentService(commentRepository, ticketRepository, membershipRepository);

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(residentUserId)
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000003")
                .title("Test ticket")
                .description("Test description")
                .status(TicketStatus.SUBMITTED)
                .build();
        ticket.setPropertyId(propertyId);

        Membership residentMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentUserId)
                .role(residentRole)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        residentMembership.setPropertyId(propertyId);

        CreateCommentRequest request = new CreateCommentRequest(noteContent, CommentVisibility.INTERNAL);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(residentUserId, propertyId))
                .thenReturn(List.of(residentMembership));

        // Act & Assert: should throw AccessDeniedException
        assertThatThrownBy(() -> commentService.addComment(ticketId, request, residentUserId))
                .isInstanceOf(AccessDeniedException.class);

        // Verify no comment was saved
        verify(commentRepository, never()).save(any());
    }

    // =====================================================================
    // Property: Management/staff CAN create internal notes
    // =====================================================================

    /**
     * For any user with a management/staff role (PROPERTY_MANAGER, TECHNICIAN,
     * VENDOR_TECHNICIAN), creating an internal note SHALL succeed and the saved
     * comment SHALL have INTERNAL visibility.
     *
     * <p><b>Validates: Requirements 10.2</b></p>
     */
    @Property(tries = 100)
    void managementAndStaffShouldBeAbleToCreateInternalNotes(
            @ForAll("internalNoteAllowedRoles") Role staffRole,
            @ForAll("noteContents") String noteContent
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();

        CommentRepository commentRepository = mock(CommentRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);

        CommentService commentService = new CommentService(commentRepository, ticketRepository, membershipRepository);

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000004")
                .title("Test ticket")
                .description("Test description")
                .status(TicketStatus.IN_PROGRESS)
                .build();
        ticket.setPropertyId(propertyId);

        Membership staffMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(staffUserId)
                .role(staffRole)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        staffMembership.setPropertyId(propertyId);

        CreateCommentRequest request = new CreateCommentRequest(noteContent, CommentVisibility.INTERNAL);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(staffUserId, propertyId))
                .thenReturn(List.of(staffMembership));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            return c;
        });

        // Act
        Comment result = commentService.addComment(ticketId, request, staffUserId);

        // Assert: the comment should be saved with INTERNAL visibility
        assertThat(result).isNotNull();
        assertThat(result.getVisibility()).isEqualTo(CommentVisibility.INTERNAL);
        assertThat(result.getAuthorId()).isEqualTo(staffUserId);
        assertThat(result.getContent()).isEqualTo(noteContent);

        // Verify save was called
        verify(commentRepository).save(any(Comment.class));
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    @Provide
    Arbitrary<Role> residentRoles() {
        return Arbitraries.of(Role.RESIDENT_OWNER, Role.RESIDENT_TENANT);
    }

    @Provide
    Arbitrary<Role> internalNoteAllowedRoles() {
        return Arbitraries.of(Role.PROPERTY_MANAGER, Role.TECHNICIAN, Role.VENDOR_TECHNICIAN);
    }

    @Provide
    Arbitrary<String> noteContents() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(500)
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', '!', '?', '-', '\n');
    }
}
