package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.common.api.PageResult;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Read-only audit trail browsing. Audit rows are append-only: this service never updates or deletes
 * them, and the console exposes no write endpoint for them (ADR-058 lineage).
 */
@Service
public class AdminAuditQueryService {
    private final AdminAuditLogRepository logs;
    public AdminAuditQueryService(AdminAuditLogRepository logs){this.logs=logs;}

    @Transactional(readOnly=true)
    public PageResult<AdminDtos.AuditView> list(int page,int size,String adminId,AdminAuditAction action,
                                                String type,Instant from,Instant to){
        String actor=blank(adminId),target=blank(type);
        return PageResult.from(logs.findAdmin(actor,action,target,from,to,PageRequest.of(page,size,
                Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id"))))
                .map(AdminDtos::of));
    }

    /** Newest actions for the dashboard feed. */
    @Transactional(readOnly=true)
    public List<AdminDtos.AuditView> recent(int limit){
        return logs.findByOrderByCreatedAtDescIdDesc(PageRequest.of(0,Math.max(1,limit)))
                .stream().map(AdminDtos::of).toList();
    }

    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
