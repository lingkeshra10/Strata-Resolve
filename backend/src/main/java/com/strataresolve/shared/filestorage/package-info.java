/**
 * File storage infrastructure for the StrataResolve platform.
 *
 * <p>Provides an abstracted {@link com.strataresolve.shared.filestorage.FileStorageService}
 * interface that decouples business logic from the underlying storage implementation.
 * The MVP uses {@link com.strataresolve.shared.filestorage.LocalFileStorageService}
 * for local filesystem storage with a UUID-based directory structure.
 *
 * <p>Supports future migration to cloud storage providers (S3, Azure Blob, GCS)
 * by implementing the same interface.
 */
package com.strataresolve.shared.filestorage;
