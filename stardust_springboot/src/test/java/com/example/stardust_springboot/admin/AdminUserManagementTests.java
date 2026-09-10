package com.example.stardust_springboot.admin;

import com.example.stardust_springboot.admin.audit.AdminAuditAction;
import com.example.stardust_springboot.admin.audit.AdminAuditLogRepository;
import com.example.stardust_springboot.user.entity.*;
import com.example.stardust_springboot.user.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserManagementTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired AdminAuditLogRepository audits;

    @Test
    void normalUserIsForbiddenFromTheUserDirectory() throws Exception {
        Account normal = register("mgmt-normal");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(normal.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void bannedAdminCannotAccessAdminApi() throws Exception {
        Account admin = register("mgmt-banned-admin");
        grant(admin.userId(), "ADMIN");
        AppUser entity = users.findById(admin.userId()).orElseThrow();
        entity.changeStatus(UserStatus.BANNED, "revoked", null);
        users.saveAndFlush(entity);

        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void adminCanListSearchPaginateAndFilterByStatusAndRole() throws Exception {
        Account admin = register("mgmt-list-admin");
        grant(admin.userId(), "ADMIN");
        Account target = register("mgmt-list-target");

        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.size").value(20));

        mvc.perform(get("/api/v1/admin/users").param("search", target.email().split("@")[0])
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mvc.perform(get("/api/v1/admin/users").param("role", "ADMIN")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.roles contains 'ADMIN')]").exists());
    }

    @Test
    void adminCanCreateUpdateAndTheActionIsAudited() throws Exception {
        Account superAdmin = register("mgmt-create-super");
        grant(superAdmin.userId(), "SUPER_ADMIN");

        String email = "created-" + UUID.randomUUID() + "@example.com";
        MvcResult created = mvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(superAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"New One\","
                                + "\"password\":\"StrongPassword!123\",\"roles\":[\"USER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_CREATE
                && id.equals(log.getTargetResourceId()));

        mvc.perform(patch("/api/v1/admin/users/{id}", id)
                        .header("Authorization", bearer(superAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Renamed"));
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_UPDATE
                && id.equals(log.getTargetResourceId()));
    }

    @Test
    void statusTransitionsProduceCorrectAuditActions() throws Exception {
        Account superAdmin = register("mgmt-status-super");
        grant(superAdmin.userId(), "SUPER_ADMIN");
        Account target = register("mgmt-status-target");

        // ban
        mvc.perform(patch("/api/v1/admin/users/{id}/status", target.publicId())
                        .header("Authorization", bearer(superAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("BANNED"));
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_BAN
                && target.publicId().equals(log.getTargetResourceId()));

        // unban (back to NORMAL)
        mvc.perform(patch("/api/v1/admin/users/{id}/status", target.publicId())
                        .header("Authorization", bearer(superAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"NORMAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NORMAL"));
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_UNBAN
                && target.publicId().equals(log.getTargetResourceId()));

        // disable
        mvc.perform(patch("/api/v1/admin/users/{id}/status", target.publicId())
                        .header("Authorization", bearer(superAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_DISABLE
                && target.publicId().equals(log.getTargetResourceId()));

        // enable (back to NORMAL) -> USER_ENABLE
        mvc.perform(patch("/api/v1/admin/users/{id}/status", target.publicId())
                        .header("Authorization", bearer(superAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"NORMAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NORMAL"));
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_ENABLE
                && target.publicId().equals(log.getTargetResourceId()));
    }

    @Test
    void deleteSoftDeletesAndAuditThenDetailStaysReadable() throws Exception {
        Account superAdmin = register("mgmt-del-super");
        grant(superAdmin.userId(), "SUPER_ADMIN");
        Account target = register("mgmt-del-target");

        mvc.perform(delete("/api/v1/admin/users/{id}", target.publicId())
                        .header("Authorization", bearer(superAdmin.token())))
                .andExpect(status().isNoContent());
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_DELETE
                && target.publicId().equals(log.getTargetResourceId()));

        mvc.perform(get("/api/v1/admin/users/{id}", target.publicId())
                        .header("Authorization", bearer(superAdmin.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DELETED"));

        mvc.perform(post("/api/v1/admin/users/{id}/restore", target.publicId())
                        .header("Authorization", bearer(superAdmin.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NORMAL"));
    }

    @Test
    void adminCanResetPassword() throws Exception {
        Account superAdmin = register("mgmt-pw-super");
        grant(superAdmin.userId(), "SUPER_ADMIN");
        Account target = register("mgmt-pw-target");

        mvc.perform(post("/api/v1/admin/users/{id}/reset-password", target.publicId())
                        .header("Authorization", bearer(superAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"AnotherStrong!123\"}"))
                .andExpect(status().isNoContent());
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_RESET_PASSWORD
                && target.publicId().equals(log.getTargetResourceId()));
    }

    @Test
    void adminCanChangeRoles() throws Exception {
        Account superAdmin = register("mgmt-role-super");
        grant(superAdmin.userId(), "SUPER_ADMIN");
        Account target = register("mgmt-role-target");

        mvc.perform(put("/api/v1/admin/users/{id}/roles", target.publicId())
                        .header("Authorization", bearer(superAdmin.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"USER\",\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray());
        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_ROLES_UPDATE
                && target.publicId().equals(log.getTargetResourceId()));
    }

    @Test
    void adminCannotModifySuperAdminNorGrantAdminNorDeleteSelf() throws Exception {
        Account admin = register("mgmt-limit-admin");
        grant(admin.userId(), "ADMIN");
        Account superTarget = register("mgmt-limit-super");
        grant(superTarget.userId(), "SUPER_ADMIN");

        mvc.perform(patch("/api/v1/admin/users/{id}/status", superTarget.publicId())
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));

        Account other = register("mgmt-limit-other");
        mvc.perform(put("/api/v1/admin/users/{id}/roles", other.publicId())
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"USER\",\"ADMIN\"]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));

        mvc.perform(delete("/api/v1/admin/users/{id}", admin.publicId())
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
    }

    private Account register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPassword!123\","
                                + "\"displayName\":\"Tester\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        AppUser user = users.findByEmailNormalizedAndDeletedAtIsNull(email).orElseThrow();
        return new Account(user.getId(), user.getPublicId(), token, email);
    }

    private void grant(Long userId, String roleCode) {
        AppUser user = users.findById(userId).orElseThrow();
        Role role = roles.findByCode(roleCode).orElseThrow();
        if (!userRoles.existsByUserIdAndRoleId(userId, role.getId())) {
            userRoles.saveAndFlush(new UserRole(user, role, null));
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Account(Long userId, String publicId, String token, String email) {
    }
}
