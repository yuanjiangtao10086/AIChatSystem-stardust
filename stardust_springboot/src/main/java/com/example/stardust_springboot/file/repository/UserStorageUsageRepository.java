package com.example.stardust_springboot.file.repository;

import com.example.stardust_springboot.file.entity.UserStorageUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserStorageUsageRepository extends JpaRepository<UserStorageUsage, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select usage from UserStorageUsage usage where usage.userId = :userId")
    Optional<UserStorageUsage> findForUpdate(@Param("userId") Long userId);

    @Query("select coalesce(sum(usage.usedBytes), 0) from UserStorageUsage usage")
    long sumUsedBytes();
}
