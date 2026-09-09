package com.example.stardust_springboot.common;

import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.api.PageRequest;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.logging.RequestContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CommonInfrastructureTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void pageRequestAppliesDefaultsAndValidationLimits() {
        PageRequest defaults = new PageRequest(null, null);

        assertThat(defaults.page()).isZero();
        assertThat(defaults.size()).isEqualTo(20);
        assertThat(validator.validate(defaults)).isEmpty();
        assertThat(validator.validate(new PageRequest(-1, 101))).hasSize(2);
    }

    @Test
    void errorCodesAreUnique() {
        long uniqueCodes = Arrays.stream(ErrorCode.values()).map(ErrorCode::code).distinct().count();
        assertThat(uniqueCodes).isEqualTo(ErrorCode.values().length);
    }

    @Test
    void apiResultReadsRequestIdFromMdc() {
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, "request-1234");
        try {
            ApiResult<String> result = ApiResult.success("ok");
            assertThat(result.code()).isZero();
            assertThat(result.requestId()).isEqualTo("request-1234");
            assertThat(result.timestamp()).isNotNull();
            assertThat(result.errors()).isNull();
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
        }
    }
}
