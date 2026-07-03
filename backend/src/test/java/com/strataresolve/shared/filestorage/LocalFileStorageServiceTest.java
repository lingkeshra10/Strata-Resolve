package com.strataresolve.shared.filestorage;

import com.strataresolve.shared.exception.PayloadTooLargeException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.shared.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LocalFileStorageService verifying:
 * - File type validation (JPEG, PNG, PDF allowed; others rejected)
 * - File size validation
 * - Store, retrieve, and delete operations
 * - UUID-based directory structure
 */
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;
    private FileStorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        properties.setMaxFileSizeBytes(1024 * 1024); // 1 MB for tests
        storageService = new LocalFileStorageService(properties);
    }

    @Test
    void store_withValidJpegFile_storesSuccessfully() throws IOException {
        byte[] content = "fake jpeg content".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("photo.jpg", "image/jpeg", content.length);

        FileReference reference = storageService.store(new ByteArrayInputStream(content), metadata);

        assertThat(reference).isNotNull();
        assertThat(reference.getStorageReference()).isNotBlank();
        assertThat(reference.getMetadata()).isEqualTo(metadata);

        // Verify file can be retrieved
        try (InputStream retrieved = storageService.retrieve(reference)) {
            assertThat(retrieved.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void store_withValidPngFile_storesSuccessfully() throws IOException {
        byte[] content = "fake png content".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("image.png", "image/png", content.length);

        FileReference reference = storageService.store(new ByteArrayInputStream(content), metadata);

        assertThat(reference).isNotNull();
        assertThat(reference.getStorageReference()).endsWith(".png");
    }

    @Test
    void store_withValidPdfFile_storesSuccessfully() throws IOException {
        byte[] content = "fake pdf content".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("document.pdf", "application/pdf", content.length);

        FileReference reference = storageService.store(new ByteArrayInputStream(content), metadata);

        assertThat(reference).isNotNull();
        assertThat(reference.getStorageReference()).endsWith(".pdf");
    }

    @Test
    void store_withUnsupportedFileType_throwsUnsupportedFileTypeException() {
        byte[] content = "text content".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("readme.txt", "text/plain", content.length);

        assertThatThrownBy(() -> storageService.store(new ByteArrayInputStream(content), metadata))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("text/plain");
    }

    @Test
    void store_withGifFile_throwsUnsupportedFileTypeException() {
        byte[] content = "gif content".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("animation.gif", "image/gif", content.length);

        assertThatThrownBy(() -> storageService.store(new ByteArrayInputStream(content), metadata))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("image/gif");
    }

    @Test
    void store_withNullContentType_throwsUnsupportedFileTypeException() {
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("file.bin", "application/octet-stream", content.length);

        assertThatThrownBy(() -> storageService.store(new ByteArrayInputStream(content), metadata))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void store_withFileSizeExceedingLimit_throwsPayloadTooLargeException() {
        // Metadata reports size larger than limit (we don't need actual large content for validation)
        FileMetadata metadata = createMetadata("large.pdf", "application/pdf", 2 * 1024 * 1024); // 2 MB

        assertThatThrownBy(() -> storageService.store(new ByteArrayInputStream(new byte[0]), metadata))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("exceeds the maximum");
    }

    @Test
    void store_withFileSizeAtLimit_storesSuccessfully() {
        FileMetadata metadata = createMetadata("limit.pdf", "application/pdf", 1024 * 1024); // Exactly 1 MB

        FileReference reference = storageService.store(new ByteArrayInputStream(new byte[0]), metadata);

        assertThat(reference).isNotNull();
    }

    @Test
    void store_generatesUuidBasedDirectoryStructure() {
        byte[] content = "test".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("test.jpg", "image/jpeg", content.length);

        FileReference reference = storageService.store(new ByteArrayInputStream(content), metadata);

        // Storage reference should have format: xx/yy/fullUUID.ext
        String ref = reference.getStorageReference();
        String[] parts = ref.split("/");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).hasSize(2); // first 2 hex chars
        assertThat(parts[1]).hasSize(2); // next 2 hex chars
        assertThat(parts[2]).matches("[0-9a-f]{32}\\.jpg"); // full UUID (no hyphens) + extension
    }

    @Test
    void retrieve_withNonExistentFile_throwsResourceNotFoundException() {
        FileMetadata metadata = createMetadata("ghost.pdf", "application/pdf", 100);
        FileReference reference = new FileReference("aa/bb/nonexistent.pdf", metadata);

        assertThatThrownBy(() -> storageService.retrieve(reference))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void delete_removesStoredFile() {
        byte[] content = "to delete".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("delete-me.png", "image/png", content.length);

        FileReference reference = storageService.store(new ByteArrayInputStream(content), metadata);

        // Delete the file
        storageService.delete(reference);

        // Verify file is gone
        assertThatThrownBy(() -> storageService.retrieve(reference))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_withNonExistentFile_doesNotThrow() {
        FileMetadata metadata = createMetadata("ghost.pdf", "application/pdf", 100);
        FileReference reference = new FileReference("aa/bb/nonexistent.pdf", metadata);

        // Should not throw - graceful handling
        storageService.delete(reference);
    }

    @Test
    void store_preservesFileExtension() {
        byte[] content = "jpeg data".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("photo.JPEG", "image/jpeg", content.length);

        FileReference reference = storageService.store(new ByteArrayInputStream(content), metadata);

        assertThat(reference.getStorageReference()).endsWith(".JPEG");
    }

    @Test
    void store_handlesFilenameWithoutExtension() {
        byte[] content = "no ext".getBytes(StandardCharsets.UTF_8);
        FileMetadata metadata = createMetadata("noextension", "image/jpeg", content.length);

        FileReference reference = storageService.store(new ByteArrayInputStream(content), metadata);

        // Should still work - UUID filename with no extension
        assertThat(reference.getStorageReference()).isNotBlank();
        String[] parts = reference.getStorageReference().split("/");
        assertThat(parts[2]).matches("[0-9a-f]{32}");
    }

    private FileMetadata createMetadata(String filename, String contentType, long fileSize) {
        return new FileMetadata(filename, contentType, fileSize, Instant.now(), UUID.randomUUID());
    }
}
