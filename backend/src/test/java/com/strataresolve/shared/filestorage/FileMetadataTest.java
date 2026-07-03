package com.strataresolve.shared.filestorage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FileMetadata value object.
 */
class FileMetadataTest {

    @Test
    void constructor_withValidArgs_createsInstance() {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();

        FileMetadata metadata = new FileMetadata("file.pdf", "application/pdf", 1024, now, userId);

        assertThat(metadata.getOriginalFilename()).isEqualTo("file.pdf");
        assertThat(metadata.getContentType()).isEqualTo("application/pdf");
        assertThat(metadata.getFileSize()).isEqualTo(1024);
        assertThat(metadata.getUploadedAt()).isEqualTo(now);
        assertThat(metadata.getUploadedBy()).isEqualTo(userId);
    }

    @Test
    void constructor_withNullFilename_throwsNullPointerException() {
        assertThatThrownBy(() -> new FileMetadata(null, "image/png", 100, Instant.now(), UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("originalFilename");
    }

    @Test
    void constructor_withNullContentType_throwsNullPointerException() {
        assertThatThrownBy(() -> new FileMetadata("file.png", null, 100, Instant.now(), UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("contentType");
    }

    @Test
    void constructor_withNegativeFileSize_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new FileMetadata("file.png", "image/png", -1, Instant.now(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileSize");
    }

    @Test
    void constructor_withNullUploadedAt_throwsNullPointerException() {
        assertThatThrownBy(() -> new FileMetadata("file.png", "image/png", 100, null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("uploadedAt");
    }

    @Test
    void constructor_withNullUploadedBy_throwsNullPointerException() {
        assertThatThrownBy(() -> new FileMetadata("file.png", "image/png", 100, Instant.now(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("uploadedBy");
    }

    @Test
    void equals_withSameValues_returnsTrue() {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();

        FileMetadata a = new FileMetadata("file.pdf", "application/pdf", 1024, now, userId);
        FileMetadata b = new FileMetadata("file.pdf", "application/pdf", 1024, now, userId);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equals_withDifferentValues_returnsFalse() {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();

        FileMetadata a = new FileMetadata("file.pdf", "application/pdf", 1024, now, userId);
        FileMetadata b = new FileMetadata("other.pdf", "application/pdf", 1024, now, userId);

        assertThat(a).isNotEqualTo(b);
    }
}
