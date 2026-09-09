package com.example.stardust_springboot.admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    @Query("""
            select log from AdminAuditLog log
            where (:adminId is null or log.admin.publicId = :adminId)
              and (:action is null or log.action = :action)
              and (:targetType is null or log.targetResourceType = :targetType)
            """)
    Page<AdminAuditLog> findAdmin(@Param("adminId") String adminId,
                                  @Param("action") AdminAuditAction action,
                                  @Param("targetType") String targetType,
                                  Pageable pageable);
}
