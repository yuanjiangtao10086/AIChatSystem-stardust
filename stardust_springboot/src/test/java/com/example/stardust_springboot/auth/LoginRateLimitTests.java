package com.example.stardust_springboot.auth;

import com.example.stardust_springboot.auth.dto.LoginRequest;
import com.example.stardust_springboot.auth.dto.RegisterRequest;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.auth.service.AuthService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "app.security.rate-limit.login-max-failures=2",
        "app.security.rate-limit.login-window=PT10M",
        "app.security.rate-limit.login-block=PT15M"
})
@Transactional
class LoginRateLimitTests {
    private static final String PASSWORD = "StrongPassword!123";
    private static final String WRONG = "WrongPassword!123";

    @Autowired AuthService auth;

    @Test
    void repeatedFailuresBlockFurtherAttemptsEvenWithTheCorrectPassword() {
        String email = register("blocked");

        assertErrorCode(() -> auth.login(new LoginRequest(email, WRONG), "10.1.0.1"),
                ErrorCode.INVALID_CREDENTIALS);
        assertErrorCode(() -> auth.login(new LoginRequest(email, WRONG), "10.1.0.1"),
                ErrorCode.INVALID_CREDENTIALS);
        assertErrorCode(() -> auth.login(new LoginRequest(email, PASSWORD), "10.1.0.1"),
                ErrorCode.RATE_LIMITED);
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        String email = register("reset");

        assertErrorCode(() -> auth.login(new LoginRequest(email, WRONG), "10.2.0.1"),
                ErrorCode.INVALID_CREDENTIALS);
        assertThat(auth.login(new LoginRequest(email, PASSWORD), "10.2.0.1")).isNotNull();
        assertErrorCode(() -> auth.login(new LoginRequest(email, WRONG), "10.2.0.1"),
                ErrorCode.INVALID_CREDENTIALS);
        assertThat(auth.login(new LoginRequest(email, PASSWORD), "10.2.0.1")).isNotNull();
    }

    @Test
    void failuresAreScopedToTheClientAddress() {
        String email = register("scope");

        assertErrorCode(() -> auth.login(new LoginRequest(email, WRONG), "10.3.0.1"),
                ErrorCode.INVALID_CREDENTIALS);
        assertThat(auth.login(new LoginRequest(email, PASSWORD), "10.3.0.2")).isNotNull();
    }

    private String register(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@test.local";
        auth.register(new RegisterRequest(email, PASSWORD, prefix), "10.0.0.1");
        return email;
    }

    private void assertErrorCode(ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(expected);
    }
}
