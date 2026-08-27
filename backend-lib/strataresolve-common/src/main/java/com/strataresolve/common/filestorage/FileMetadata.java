package com.strataresolve.common.filestorage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing metadata associated with a stored file.
 * Stores the original filename, content type, file size, upload timestamp, and uploader identity.
 */
public final class FileMetadata {

    private final String originalFilename;
    private final String contentType;
    private final long fileSize;
    private final Instant uploadedAt;
    private final UUID uploadedBy;

    public FileMetadata(String originalFilename, String contentType, long fileSize, Instant uploadedAt, UUID uploadedBy) {
        Objects.requireNonNull(originalFilename, "originalFilename must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
        Objects.requireNonNull(uploadedBy, "uploadedBy must not be null");
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadata that = (FileMetadata) o;
        return fileSize == that.fileSize
                && Objects.equals(originalFilename, that.originalFilename)
                && Objects.equals(contentType, that.contentType)
                && Objects.equals(uploadedAt, that.uploadedAt)
                && Objects.equals(uploadedBy, that.uploadedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalFilename, contentType, fileSize, uploadedAt, uploadedBy);
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "originalFilename='" + originalFilename + '\'' +
                ", contentType='" + contentType + '\'' +
                ", fileSize=" + fileSize +
                ", uploadedAt=" + uploadedAt +
                ", uploadedBy=" + uploadedBy +
                '}';
    }
}
