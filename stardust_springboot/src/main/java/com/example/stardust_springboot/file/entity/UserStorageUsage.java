package com.example.stardust_springboot.file.entity;

import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "user_storage_usage")
@EntityListeners(AuditingEntityListener.class)
public class UserStorageUsage {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;
    @Column(name = "used_bytes", nullable = false)
    private long usedBytes;
    @Column(name = "reserved_bytes", nullable = false)
    private long reservedBytes;
    @Column(name = "file_count", nullable = false)
    private long fileCount;
    @Column(name = "quota_bytes", nullable = false)
    private long quotaBytes;
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected UserStorageUsage() {
    }

    public UserStorageUsage(AppUser user, long quotaBytes) {
        this.user = user;
        this.quotaBytes = quotaBytes;
    }

    public boolean canReserve(long bytes) { return usedBytes + reservedBytes + bytes <= quotaBytes; }
    public void reserve(long bytes) { reservedBytes += bytes; }
    public void complete(long bytes) { reservedBytes -= bytes; usedBytes += bytes; fileCount++; }
    public void release(long bytes) { reservedBytes -= bytes; }
    public void remove(long bytes) { usedBytes -= bytes; fileCount--; }

    public long getUsedBytes() { return usedBytes; }
    public long getReservedBytes() { return reservedBytes; }
    public long getFileCount() { return fileCount; }
    public long getQuotaBytes() { return quotaBytes; }
}
