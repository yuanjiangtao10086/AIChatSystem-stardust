package com.example.stardust_springboot.auth;

import com.example.stardust_springboot.auth.service.RefreshCookieService;
import com.example.stardust_springboot.auth.security.RefreshCsrfGuardFilter;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.entity.UserStatus;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import com.example.stardust_springboot.user.repository.RoleRepository;
import com.example.stardust_springboot.user.repository.UserRoleRepository;
import com.example.stardust_springboot.user.entity.UserRole;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTests {

    private static final String PASSWORD = "StrongPassword!123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void normalLoginReturnsAccessTokenAndCurrentUser() throws Exception {
        String email = uniqueEmail("login");
        register(email);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.roles[0]").value("USER"))
                .andReturn();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NORMAL"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String email = uniqueEmail("wrong-password");
        register(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "DefinitelyWrong!123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40104));
    }

    @Test
    void bannedUserIsRejectedByProtectedEndpointAndLogin() throws Exception {
        String email = uniqueEmail("banned");
        MvcResult registration = register(email);
        AppUser user = userRepository.findByEmailNormalizedAndDeletedAtIsNull(email).orElseThrow();
        user.changeStatus(UserStatus.BANNED, "test ban", null);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken(registration)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void disabledUserIsRejectedByProtectedEndpoint() throws Exception {
        String email = uniqueEmail("disabled");
        MvcResult registration = register(email);
        AppUser user = userRepository.findByEmailNormalizedAndDeletedAtIsNull(email).orElseThrow();
        user.changeStatus(UserStatus.DISABLED, null, null);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken(registration)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40304));
    }

    @Test
    void normalUserCannotAccessAdminApi() throws Exception {
        MvcResult registration = register(uniqueEmail("rbac"));

        mockMvc.perform(get("/api/v1/admin/access-check")
                        .header("Authorization", "Bearer " + accessToken(registration)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void administratorRolesCanAccessAdminApi(String roleCode) throws Exception {
        String email = uniqueEmail(roleCode.toLowerCase(Locale.ROOT));
        MvcResult registration = register(email);
        AppUser user = userRepository.findByEmailNormalizedAndDeletedAtIsNull(email).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(
                user, roleRepository.findByCode(roleCode).orElseThrow(), null));

        mockMvc.perform(get("/api/v1/admin/access-check")
                        .header("Authorization", "Bearer " + accessToken(registration)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true));
    }

    @Test
    void invalidAccessTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesFamily() throws Exception {
        MvcResult registration = register(uniqueEmail("refresh"));
        Cookie original = refreshCookie(registration);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(original)
                        .header(RefreshCsrfGuardFilter.HEADER, RefreshCsrfGuardFilter.EXPECTED_VALUE))
                .andExpect(status().isOk())
                .andReturn();
        Cookie replacement = refreshCookie(refreshed);
        assertThat(replacement.getValue()).isNotEqualTo(original.getValue());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(original)
                        .header(RefreshCsrfGuardFilter.HEADER, RefreshCsrfGuardFilter.EXPECTED_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(replacement)
                        .header(RefreshCsrfGuardFilter.HEADER, RefreshCsrfGuardFilter.EXPECTED_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40105));
    }

    @Test
    void refreshCookieCannotBeUsedWithoutCsrfGuardHeader() throws Exception {
        MvcResult registration = register(uniqueEmail("csrf"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(registration)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40305));
    }

    @Test
    void passwordChangeInvalidatesOldAccessAndRefreshTokens() throws Exception {
        MvcResult registration = register(uniqueEmail("password-change"));
        String oldAccessToken = accessToken(registration);
        Cookie oldRefreshToken = refreshCookie(registration);

        MvcResult changed = mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + oldAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"StrongPassword!123","newPassword":"NewStrongPassword!456"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(oldRefreshToken)
                        .header(RefreshCsrfGuardFilter.HEADER, RefreshCsrfGuardFilter.EXPECTED_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40105));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken(changed)))
                .andExpect(status().isOk());
    }

    @Test
    void profileCanBeUpdatedAndLogoutRevokesRefreshToken() throws Exception {
        MvcResult registration = register(uniqueEmail("profile"));
        String accessToken = accessToken(registration);
        Cookie refreshToken = refreshCookie(registration);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated name"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshToken)
                        .header(RefreshCsrfGuardFilter.HEADER, RefreshCsrfGuardFilter.EXPECTED_VALUE))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshToken)
                        .header(RefreshCsrfGuardFilter.HEADER, RefreshCsrfGuardFilter.EXPECTED_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40105));
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Test user"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.roles[0]").value("USER"))
                .andReturn();
    }

    private String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshCookieService.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }

    private String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String uniqueEmail(String prefix) {
        return (prefix + "-" + UUID.randomUUID() + "@example.com").toLowerCase(Locale.ROOT);
    }
}
