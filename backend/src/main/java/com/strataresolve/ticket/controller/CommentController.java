package com.strataresolve.ticket.controller;

import com.strataresolve.ticket.domain.Comment;
import com.strataresolve.ticket.dto.CommentResponse;
import com.strataresolve.ticket.dto.CreateCommentRequest;
import com.strataresolve.ticket.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing comments on tickets.
 * Provides endpoints for adding comments and retrieving comments with visibility filtering.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * Adds a comment to a ticket.
     *
     * <p>Any authenticated user with access to the ticket can add a PUBLIC comment.
     * INTERNAL notes are restricted to Property Managers, Technicians, and Vendor Technicians.
     *
     * @param ticketId       the ticket to add the comment to
     * @param request        the comment creation request
     * @param authentication the authenticated user
     * @return the created comment with HTTP 201
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable UUID ticketId,
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication) {
        UUID authorId = (UUID) authentication.getPrincipal();
        Comment comment = commentService.addComment(ticketId, request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
    }

    /**
     * Retrieves all comments for a ticket.
     *
     * <p>Internal notes are automatically filtered out for resident users.
     * Management and staff users see all comments including internal notes.
     *
     * @param ticketId       the ticket to get comments for
     * @param authentication the authenticated user
     * @return list of comments visible to the requesting user
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable UUID ticketId,
            Authentication authentication) {
        UUID requestingUserId = (UUID) authentication.getPrincipal();
        List<CommentResponse> comments = commentService.getCommentsForTicket(ticketId, requestingUserId)
                .stream()
                .map(CommentResponse::from)
                .toList();
        return ResponseEntity.ok(comments);
    }
}
