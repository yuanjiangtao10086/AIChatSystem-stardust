package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.ai.request.AiRequestLogRepository;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.auth.service.RefreshTokenService;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.config.StorageProperties;
import com.example.stardust_springboot.file.entity.UserStorageUsage;
import com.example.stardust_springboot.file.repository.UserStorageUsageRepository;
import com.example.stardust_springboot.user.entity.*;
import com.example.stardust_springboot.user.repository.*;
import com.example.stardust_springboot.usage.service.AiUsageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.util.*;

@Service
public class AdminUserService {
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final UserStorageUsageRepository storage;
    private final AiUsageService usage;
    private final AiRequestLogRepository requests;
    private final PasswordEncoder passwords;
    private final RefreshTokenService refreshTokens;
    private final AdminAuthorizationService authorization;
    private final AdminAuditService audit;
    private final Clock clock;
    private final long defaultQuota;

    public AdminUserService(AppUserRepository users, RoleRepository roles, UserRoleRepository userRoles,
                            UserStorageUsageRepository storage, AiUsageService usage,
                            AiRequestLogRepository requests,
                            PasswordEncoder passwords, RefreshTokenService refreshTokens,
                            AdminAuthorizationService authorization, AdminAuditService audit,
                            StorageProperties properties, Clock clock) {
        this.users=users; this.roles=roles; this.userRoles=userRoles; this.storage=storage;
        this.usage=usage; this.requests=requests; this.passwords=passwords; this.refreshTokens=refreshTokens;
        this.authorization=authorization; this.audit=audit; this.clock=clock;
        this.defaultQuota=properties.defaultQuotaBytes();
    }

    @Transactional(readOnly=true)
    public PageResult<AdminDtos.UserView> list(int page, int size, String search, UserStatus status,
                                               String role, Sort.Direction direction) {
        Sort sort=Sort.by(direction==null?Sort.Direction.DESC:direction,"createdAt");
        return PageResult.from(users.findAdmin(normalizeBlank(search), status, normalizeBlank(role),
                PageRequest.of(page,size,sort))
                .map(this::view));
    }

    /** Administrative detail. Deleted users stay readable so operators can inspect an audit target. */
    @Transactional(readOnly=true)
    public AdminDtos.UserView get(String id) {
        return view(users.findByPublicId(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional
    public AdminDtos.UserView create(AuthenticatedUser actor, AdminDtos.CreateUser request) {
        authorization.validateRoleAssignment(actor, request.roles());
        String email=normalizeEmail(request.email());
        if(users.existsByEmailNormalized(email)) throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        AppUser user=users.saveAndFlush(new AppUser(email,passwords.encode(request.password()),request.displayName().trim()));
        assignRoles(user,request.roles(),users.getReferenceById(actor.id()));
        storage.save(new UserStorageUsage(user,defaultQuota));
        usage.createAccount(user);
        audit.record(actor,AdminAuditAction.USER_CREATE,user,"USER",user.getPublicId(),null);
        return view(user);
    }

    @Transactional
    public AdminDtos.UserView update(AuthenticatedUser actor,String id,AdminDtos.UpdateUser request) {
        AppUser user=require(id); authorization.requireCanManage(actor,user);
        String email=normalizeEmail(request.email());
        users.findByEmailNormalizedAndDeletedAtIsNull(email).filter(found -> !found.getId().equals(user.getId()))
                .ifPresent(found -> { throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS); });
        user.adminUpdate(email,request.displayName().trim());
        audit.record(actor,AdminAuditAction.USER_UPDATE,user,"USER",id,null);
        return view(user);
    }

    @Transactional
    public AdminDtos.UserView changeStatus(AuthenticatedUser actor,String id,AdminDtos.UserStatusChange request) {
        AppUser user=require(id); authorization.requireCanManage(actor,user);
        if(request.status()==UserStatus.DELETED) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
        UserStatus before=user.getStatus();
        user.changeStatus(request.status(),request.reason(),request.bannedUntil());
        if(request.status()!=UserStatus.NORMAL) refreshTokens.revokeAll(user.getId());
        audit.record(actor,statusAction(before,request.status()),user,"USER",id,
                "{\"from\":\""+before+"\",\"to\":\""+request.status()+"\"}");
        return view(user);
    }

    private AdminAuditAction statusAction(UserStatus before,UserStatus target){
        return switch(target){
            case BANNED -> AdminAuditAction.USER_BAN;
            case DISABLED -> AdminAuditAction.USER_DISABLE;
            case NORMAL -> switch(before){
                case BANNED -> AdminAuditAction.USER_UNBAN;
                case DISABLED -> AdminAuditAction.USER_ENABLE;
                default -> AdminAuditAction.USER_UPDATE;
            };
            case DELETED -> throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
        };
    }

    @Transactional
    public AdminDtos.UserView restore(AuthenticatedUser actor,String id) {
        AppUser user=users.findByPublicId(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        authorization.requireCanManage(actor,user); user.restore();
        audit.record(actor,AdminAuditAction.USER_RESTORE,user,"USER",id,null); return view(user);
    }

    @Transactional
    public void delete(AuthenticatedUser actor,String id) {
        AppUser user=require(id); authorization.requireCanManage(actor,user); user.softDelete();
        refreshTokens.revokeAll(user.getId());
        audit.record(actor,AdminAuditAction.USER_DELETE,user,"USER",id,null);
    }

    @Transactional
    public void resetPassword(AuthenticatedUser actor,String id,AdminDtos.ResetPassword request) {
        AppUser user=require(id); authorization.requireCanManage(actor,user);
        user.changePassword(passwords.encode(request.password()),clock.instant()); refreshTokens.revokeAll(user.getId());
        audit.record(actor,AdminAuditAction.USER_RESET_PASSWORD,user,"USER",id,null);
    }

    @Transactional
    public AdminDtos.UserView changeRoles(AuthenticatedUser actor,String id,AdminDtos.RoleChange request) {
        AppUser user=require(id); authorization.requireCanManage(actor,user);
        authorization.validateRoleAssignment(actor,request.roles());
        Set<String> current=new HashSet<>(userRoles.findEnabledRoleCodesByUserId(user.getId()));
        for(String code:current) if(!request.roles().contains(code)) {
            Role role=roles.findByCode(code).orElseThrow(); userRoles.deleteByUserIdAndRoleId(user.getId(),role.getId());
        }
        assignRoles(user,request.roles(),users.getReferenceById(actor.id()));
        audit.record(actor,AdminAuditAction.USER_ROLES_UPDATE,user,"USER",id,
                "{\"roles\":\""+String.join(",",request.roles())+"\"}");
        return view(user);
    }

    private void assignRoles(AppUser user,Set<String> requested,AppUser grantor) {
        for(String code:requested) {
            Role role=roles.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION));
            if(!userRoles.existsByUserIdAndRoleId(user.getId(),role.getId())) userRoles.save(new UserRole(user,role,grantor));
        }
    }
    private AppUser require(String id){ return users.findByPublicIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)); }
    @Transactional
    public AdminDtos.UserView adjustUsage(AuthenticatedUser actor,String id,AdminDtos.UsageAdjust request){
        AppUser user=require(id); authorization.requireCanManage(actor,user);
        usage.adjust(user.getId(), AiUsageService.KEY_ADJUST_PREFIX + PublicIdGenerator.newUlid(),
                request.tokenDelta(), request.costDelta());
        audit.record(actor,AdminAuditAction.USER_USAGE_ADJUST,user,"USER_USAGE",id,
                "{\"tokenDelta\":"+request.tokenDelta()
                        +",\"costDelta\":\""+request.costDelta().toPlainString()
                        +"\",\"reason\":\""+json(request.reason())+"\"}");
        return view(user);
    }

    private AdminDtos.UserView view(AppUser user){
        var storageUsage=storage.findById(user.getId()).orElse(null);
        return new AdminDtos.UserView(user.getPublicId(),user.getEmailNormalized(),user.getDisplayName(),user.getStatus(),
                user.getBanReason(),user.getBannedUntil(),user.getLastLoginAt(),
                Set.copyOf(userRoles.findEnabledRoleCodesByUserId(user.getId())),
                storageUsage==null?0:storageUsage.getUsedBytes(),storageUsage==null?0:storageUsage.getQuotaBytes(),
                requests.countByUserId(user.getId()),requests.sumTotalTokensByUserId(user.getId()),
                usage.snapshot(user.getId()),
                user.getCreatedAt(),user.getUpdatedAt());
    }
    private String json(String value){
        StringBuilder out=new StringBuilder();
        for(char c : value.toCharArray()){
            switch(c){
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if(c < 0x20) out.append(String.format("\\u%04x",(int)c)); else out.append(c); }
            }
        }
        return out.toString();
    }
    private String normalizeEmail(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);}
    private String normalizeBlank(String value){return value==null||value.isBlank()?null:value.trim();}
}
