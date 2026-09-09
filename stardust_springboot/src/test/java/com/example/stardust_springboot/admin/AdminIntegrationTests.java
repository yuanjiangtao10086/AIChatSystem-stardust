package com.example.stardust_springboot.admin;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.user.entity.*;
import com.example.stardust_springboot.user.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired AdminAuditLogRepository audits;

    @Test
    void normalUserCannotAccessAdminApiAndAdminCanReadDashboard() throws Exception {
        Account normal=register("normal");
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization",bearer(normal.token())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        Account admin=register("admin"); grant(admin.userId(),"ADMIN");
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization",bearer(admin.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalUsers").isNumber());
    }

    @Test
    void adminCannotManageSuperAdminButSuperAdminCanBanUserAndAuditIt() throws Exception {
        Account admin=register("limited-admin");grant(admin.userId(),"ADMIN");
        Account superAdmin=register("super-target");grant(superAdmin.userId(),"SUPER_ADMIN");
        mvc.perform(patch("/api/v1/admin/users/{id}/status",superAdmin.publicId())
                        .header("Authorization",bearer(admin.token())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"test\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));

        Account superActor=register("super-actor");grant(superActor.userId(),"SUPER_ADMIN");
        Account target=register("ban-target");
        mvc.perform(patch("/api/v1/admin/users/{id}/status",target.publicId())
                        .header("Authorization",bearer(superActor.token())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"policy violation\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("BANNED"));
        assertThat(audits.findAll()).anyMatch(log -> log.getAction()==AdminAuditAction.USER_BAN
                && target.publicId().equals(log.getTargetResourceId()));
    }

    @Test
    void viewingConversationCreatesAuditAndProviderSecretReferenceIsNeverReturned() throws Exception {
        Account owner=register("chat-owner"); Account admin=register("ops-admin");grant(admin.userId(),"ADMIN");
        MvcResult created=mvc.perform(post("/api/v1/conversations").header("Authorization",bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Audit me\"}"))
                .andExpect(status().isCreated()).andReturn();
        String conversationId=JsonPath.read(created.getResponse().getContentAsString(),"$.data.id");
        mvc.perform(get("/api/v1/admin/conversations/{id}/messages",conversationId)
                        .header("Authorization",bearer(admin.token())))
                .andExpect(status().isOk());
        assertThat(audits.findAll()).anyMatch(log -> log.getAction()==AdminAuditAction.CONVERSATION_VIEW
                && conversationId.equals(log.getTargetResourceId()) && log.getRequestId()!=null);

        String secretRef="vault:phase11-"+UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/ai/providers").header("Authorization",bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"phase11-provider","displayName":"Phase 11","type":"OPENAI_COMPATIBLE",
                         "baseUrl":"https://example.invalid/v1","credentialRef":"%s","configJson":"{}"}
                        """.formatted(secretRef)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.credentialConfigured").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(secretRef))));
        mvc.perform(get("/api/v1/admin/ai/providers").header("Authorization",bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(secretRef))));
    }

    private Account register(String prefix) throws Exception {
        String email=prefix+"-"+UUID.randomUUID()+"@example.com";
        MvcResult result=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""+email+"\",\"password\":\"StrongPassword!123\",\"displayName\":\"Tester\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token=JsonPath.read(result.getResponse().getContentAsString(),"$.data.accessToken");
        AppUser user=users.findByEmailNormalizedAndDeletedAtIsNull(email).orElseThrow();
        return new Account(user.getId(),user.getPublicId(),token);
    }
    private void grant(Long userId,String roleCode){AppUser user=users.findById(userId).orElseThrow();Role role=roles.findByCode(roleCode).orElseThrow();if(!userRoles.existsByUserIdAndRoleId(userId,role.getId()))userRoles.saveAndFlush(new UserRole(user,role,null));}
    private String bearer(String token){return "Bearer "+token;}
    private record Account(Long userId,String publicId,String token){}
}
