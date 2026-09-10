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
class AdminUsageAdjustTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired AdminAuditLogRepository audits;

    @Test
    void superAdminAdjustsQuotaAndTheActionIsAudited() throws Exception {
        Account actor = register("usage-actor");
        grant(actor.userId(), "SUPER_ADMIN");
        Account target = register("usage-target");

        mvc.perform(post("/api/v1/admin/users/{id}/usage:adjust", target.publicId())
                        .header("Authorization", bearer(actor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenDelta\":120,\"costDelta\":\"0.5\",\"reason\":\"support goodwill\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usage.usedTokens").value(120))
                .andExpect(jsonPath("$.data.usage.currency").value("USD"));

        assertThat(audits.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.USER_USAGE_ADJUST
                && target.publicId().equals(log.getTargetResourceId())
                && log.getMetadataJson() != null
                && log.getMetadataJson().contains("support goodwill"));
    }

    @Test
    void adjustmentCannotMakeConsumedUsageNegative() throws Exception {
        Account actor = register("usage-neg-actor");
        grant(actor.userId(), "SUPER_ADMIN");
        Account target = register("usage-neg-target");

        mvc.perform(post("/api/v1/admin/users/{id}/usage:adjust", target.publicId())
                        .header("Authorization", bearer(actor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenDelta\":-999,\"costDelta\":\"0\",\"reason\":\"too much\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));
    }

    @Test
    void ordinaryUserCannotAdjustQuota() throws Exception {
        Account actor = register("usage-normal");
        Account target = register("usage-normal-target");

        mvc.perform(post("/api/v1/admin/users/{id}/usage:adjust", target.publicId())
                        .header("Authorization", bearer(actor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenDelta\":10,\"costDelta\":\"0\",\"reason\":\"nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    private Account register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPassword!123\","
                                + "\"displayName\":\"Tester\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        AppUser user = users.findByEmailNormalizedAndDeletedAtIsNull(email).orElseThrow();
        return new Account(user.getId(), user.getPublicId(), token);
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

    private record Account(Long userId, String publicId, String token) {
    }
}
