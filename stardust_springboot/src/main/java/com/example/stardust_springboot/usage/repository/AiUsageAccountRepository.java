package com.example.stardust_springboot.usage.repository;

import com.example.stardust_springboot.usage.entity.AiUsageAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiUsageAccountRepository extends JpaRepository<AiUsageAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from AiUsageAccount account where account.userId = :userId")
    Optional<AiUsageAccount> findForUpdate(@Param("userId") Long userId);
}
