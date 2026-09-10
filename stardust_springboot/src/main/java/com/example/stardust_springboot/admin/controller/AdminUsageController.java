package com.example.stardust_springboot.admin.controller;

import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.usage.breakdown.BreakdownDimension;
import com.example.stardust_springboot.usage.breakdown.UsageBreakdownItem;
import com.example.stardust_springboot.usage.breakdown.UsageBreakdownService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Admin-only aggregate usage analytics. Secured by {@code /api/v1/admin/**} in {@code SecurityConfiguration}.
 */
@RestController
@RequestMapping("/api/v1/admin/usage")
public class AdminUsageController {

    private final UsageBreakdownService breakdown;

    public AdminUsageController(UsageBreakdownService breakdown) {
        this.breakdown = breakdown;
    }

    @GetMapping("/breakdown")
    public ApiResult<List<UsageBreakdownItem>> breakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "DAY") BreakdownDimension by) {
        return ApiResult.success(breakdown.breakdownGlobal(resolve(from), resolveTo(to), by));
    }

    private Instant resolve(Instant from) {
        return from == null ? Instant.now().minus(java.time.Duration.ofDays(30)) : from;
    }

    private Instant resolveTo(Instant to) {
        return to == null ? Instant.now() : to;
    }
}
