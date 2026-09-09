package com.example.stardust_springboot.file.dto;

import com.example.stardust_springboot.file.entity.UserStorageUsage;

public record StorageUsageView(long usedBytes, long reservedBytes, long quotaBytes, long fileCount) {
    public static StorageUsageView from(UserStorageUsage usage) {
        return new StorageUsageView(usage.getUsedBytes(), usage.getReservedBytes(),
                usage.getQuotaBytes(), usage.getFileCount());
    }
}
