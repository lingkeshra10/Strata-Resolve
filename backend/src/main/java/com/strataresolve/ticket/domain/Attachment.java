package com.strataresolve.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a file attachment associated with a ticket.
 * Stores metadata about the uploaded file alongside a reference to the stored file content.
 *
 * <p>Note: The attachment table does not have a property_id column directly.
 * Access control is enforced through the parent ticket's property context.
 */
@Entity
@Table(name = "attachment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_reference", nullable = false, length = 500)
    private String storageReference;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = Instant.now();
        }
    }
}
