package com.strataresolve.shared.filestorage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the file storage service.
 * Binds to the {@code app.file-storage} prefix in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {

    /**
     * Base path on the local filesystem where uploaded files are stored.
     */
    private String basePath = "./uploads";

    /**
     * Maximum allowed file size in bytes (default: 10 MB).
     */
    private long maxFileSizeBytes = 10 * 1024 * 1024;

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}
