package com.example.stardust_springboot.usage.controller;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.usage.breakdown.BreakdownDimension;
import com.example.stardust_springboot.usage.breakdown.UsageBreakdownItem;
import com.example.stardust_springboot.usage.breakdown.UsageBreakdownService;
import com.example.stardust_springboot.usage.dto.UsageView;
import com.example.stardust_springboot.usage.service.AiUsageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {
    private final AiUsageService usage;
    private final UsageBreakdownService breakdown;
    private final Clock clock;

    public UsageController(AiUsageService usage, UsageBreakdownService breakdown, Clock clock) {
        this.usage = usage;
        this.breakdown = breakdown;
        this.clock = clock;
    }

    @GetMapping
    public ApiResult<UsageView> current(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResult.success(usage.current(principal.id()));
    }

    @GetMapping("/breakdown")
    public ApiResult<List<UsageBreakdownItem>> breakdown(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "DAY") BreakdownDimension by) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        return ApiResult.success(breakdown.breakdownForUser(principal.id(), start, end, by));
    }
}
