package com.example.stardust_springboot.file.entity;

import com.example.stardust_springboot.common.persistence.SoftDeleteEntity;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_file")
public class UserFile extends SoftDeleteEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(name = "storage_name", nullable = false, updatable = false, length = 80)
    private String storageName;
    @Column(name = "object_key", nullable = false, updatable = false, length = 512)
    private String objectKey;
    @Column(name = "declared_mime", nullable = false, updatable = false, length = 127)
    private String declaredMime;
    @Column(name = "detected_mime", nullable = false, updatable = false, length = 127)
    private String detectedMime;
    @Column(nullable = false, updatable = false, length = 16)
    private String extension;
    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;
    @Column(nullable = false, updatable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sha256;
    @Column(name = "storage_provider", nullable = false, updatable = false, length = 32)
    private String storageProvider;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserFileStatus status = UserFileStatus.UPLOADING;
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    protected UserFile() {
    }

    public UserFile(AppUser user, String originalName, String storageName, String objectKey,
                    String declaredMime, String detectedMime, String extension, long sizeBytes,
                    String sha256, String storageProvider, String metadataJson) {
        this.user = user;
        this.originalName = originalName;
        this.storageName = storageName;
        this.objectKey = objectKey;
        this.declaredMime = declaredMime;
        this.detectedMime = detectedMime;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageProvider = storageProvider;
        this.metadataJson = metadataJson;
    }

    public void markAvailable() { status = UserFileStatus.AVAILABLE; }
    public void markFailed() { status = UserFileStatus.FAILED; }
    public void beginDelete() { status = UserFileStatus.DELETING; }
    public void restoreAvailable() { status = UserFileStatus.AVAILABLE; }
    public void softDelete() { status = UserFileStatus.DELETED; markDeletedAt(Instant.now()); }
    public void rename(String name) { originalName = name; }

    public AppUser getUser() { return user; }
    public String getOriginalName() { return originalName; }
    public String getStorageName() { return storageName; }
    public String getObjectKey() { return objectKey; }
    public String getDeclaredMime() { return declaredMime; }
    public String getDetectedMime() { return detectedMime; }
    public String getExtension() { return extension; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageProvider() { return storageProvider; }
    public UserFileStatus getStatus() { return status; }
    public String getMetadataJson() { return metadataJson; }
}
