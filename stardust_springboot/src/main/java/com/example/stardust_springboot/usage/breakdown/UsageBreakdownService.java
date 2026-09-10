package com.example.stardust_springboot.usage.breakdown;

import com.example.stardust_springboot.ai.request.AiRequestLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates AI request volume by day / model / provider.
 *
 * <p>Reads straight from {@code ai_request_log}; no pre-aggregated table is needed at this scale. Only
 * {@code COMPLETED} requests carry settled token counts, so they are the sole source for token sums — a
 * stopped or failed request contributes neither tokens nor a misleading count.
 */
@Service
public class UsageBreakdownService {

    private final AiRequestLogRepository requestLogs;

    public UsageBreakdownService(AiRequestLogRepository requestLogs) {
        this.requestLogs = requestLogs;
    }

    public List<UsageBreakdownItem> breakdownForUser(Long userId, Instant from, Instant to, BreakdownDimension by) {
        return map(switch (by) {
            case DAY -> requestLogs.breakdownByDayForUser(userId, from, to);
            case MODEL -> requestLogs.breakdownByModelForUser(userId, from, to);
            case PROVIDER -> requestLogs.breakdownByProviderForUser(userId, from, to);
        });
    }

    public List<UsageBreakdownItem> breakdownGlobal(Instant from, Instant to, BreakdownDimension by) {
        return map(switch (by) {
            case DAY -> requestLogs.breakdownByDayGlobal(from, to);
            case MODEL -> requestLogs.breakdownByModelGlobal(from, to);
            case PROVIDER -> requestLogs.breakdownByProviderGlobal(from, to);
        });
    }

    private List<UsageBreakdownItem> map(List<Object[]> rows) {
        List<UsageBreakdownItem> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String key = row[0] instanceof LocalDate date ? date.toString() : String.valueOf(row[0]);
            items.add(new UsageBreakdownItem(key, toLong(row[1]), toLong(row[2]),
                    toLong(row[3]), toLong(row[4])));
        }
        return items;
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
