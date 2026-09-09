package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.common.api.PageResult;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditQueryService {
    private final AdminAuditLogRepository logs;
    public AdminAuditQueryService(AdminAuditLogRepository logs){this.logs=logs;}
    @Transactional(readOnly=true)
    public PageResult<AdminDtos.AuditView> list(int page,int size,String adminId,AdminAuditAction action,String type){
        String actor=blank(adminId),target=blank(type);
        return PageResult.from(logs.findAdmin(actor,action,target,PageRequest.of(page,size,
                Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id")))).map(log->
                new AdminDtos.AuditView(log.getPublicId(),log.getAdmin().getPublicId(),log.getAdmin().getEmailNormalized(),
                        log.getAction(),log.getTargetUser()==null?null:log.getTargetUser().getPublicId(),
                        log.getTargetResourceType(),log.getTargetResourceId(),log.getIp(),log.getUserAgent(),
                        log.getRequestId(),log.getMetadataJson(),log.getCreatedAt())));
    }
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
