package com.example.stardust_springboot.admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    @Query("""
            select log from AdminAuditLog log
            where (:adminId is null or log.admin.publicId = :adminId)
              and (:action is null or log.action = :action)
              and (:targetType is null or log.targetResourceType = :targetType)
              and (:from is null or log.createdAt >= :from)
              and (:to is null or log.createdAt <= :to)
            """)
    Page<AdminAuditLog> findAdmin(@Param("adminId") String adminId,
                                  @Param("action") AdminAuditAction action,
                                  @Param("targetType") String targetType,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);

    /** Most recent audited action of a given type on a resource; used by tests and support tooling. */
    AdminAuditLog findFirstByActionAndTargetResourceIdOrderByIdDesc(AdminAuditAction action, String targetResourceId);

    /** Newest audit rows for the dashboard activity feed. */
    List<AdminAuditLog> findByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
