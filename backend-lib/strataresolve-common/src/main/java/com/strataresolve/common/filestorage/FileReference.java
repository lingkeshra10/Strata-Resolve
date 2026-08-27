package com.strataresolve.common.filestorage;

import java.util.Objects;

/**
 * Value object representing a reference to a stored file.
 * The storage reference is an opaque identifier that the storage service
 * uses to locate the file (e.g., a relative path for local storage,
 * or a key for cloud storage).
 */
public final class FileReference {

    private final String storageReference;
    private final FileMetadata metadata;

    public FileReference(String storageReference, FileMetadata metadata) {
        Objects.requireNonNull(storageReference, "storageReference must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        this.storageReference = storageReference;
        this.metadata = metadata;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public FileMetadata getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileReference that = (FileReference) o;
        return Objects.equals(storageReference, that.storageReference)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageReference, metadata);
    }

    @Override
    public String toString() {
        return "FileReference{" +
                "storageReference='" + storageReference + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
