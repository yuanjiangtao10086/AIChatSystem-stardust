package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.ai.entity.ModelStatus;
import com.example.stardust_springboot.ai.entity.ProviderStatus;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.ai.repository.AiProviderRepository;
import com.example.stardust_springboot.ai.request.*;
import com.example.stardust_springboot.conversation.repository.*;
import com.example.stardust_springboot.file.repository.*;
import com.example.stardust_springboot.knowledge.repository.KnowledgeDocumentRepository;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only aggregate view of the platform for the admin console.
 *
 * <p>Everything here is derived from existing tables: no dashboard table, no materialised rollup.
 * The hourly trend buckets the last 24 hours in Java from a two-column projection so the query
 * stays portable between MySQL and H2 (no vendor {@code hour()} and no session-timezone surprise).
 */
@Service
public class AdminDashboardService {

    /** Number of buckets in the trend; the console renders one bar per bucket. */
    private static final int TREND_HOURS = 24;

    /** Rows kept in the "recent activity" / "recent failures" feeds. */
    private static final int RECENT_LIMIT = 5;

    private final AppUserRepository users; private final ConversationRepository conversations;
    private final ChatMessageRepository messages; private final AiRequestLogRepository aiRequests;
    private final UserFileRepository files; private final UserStorageUsageRepository storage;
    private final KnowledgeDocumentRepository documents; private final AiProviderRepository providers;
    private final AiModelRepository models; private final AdminAuditQueryService audits;
    private final Clock clock;

    public AdminDashboardService(AppUserRepository users, ConversationRepository conversations,
            ChatMessageRepository messages, AiRequestLogRepository aiRequests, UserFileRepository files,
            UserStorageUsageRepository storage, KnowledgeDocumentRepository documents,
            AiProviderRepository providers, AiModelRepository models, AdminAuditQueryService audits,
            Clock clock) {
        this.users=users;this.conversations=conversations;this.messages=messages;this.aiRequests=aiRequests;
        this.files=files;this.storage=storage;this.documents=documents;this.providers=providers;
        this.models=models;this.audits=audits;this.clock=clock;
    }

    @Transactional(readOnly=true)
    public AdminDtos.Dashboard dashboard(){
        Instant now=clock.instant(); Instant today=now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant activeSince=now.minus(Duration.ofDays(30));
        return new AdminDtos.Dashboard(users.count(),users.countByCreatedAtGreaterThanEqual(today),
                users.countByLastLoginAtGreaterThanEqualAndDeletedAtIsNull(activeSince),conversations.count(),
                messages.count(),aiRequests.count(),aiRequests.sumTotalTokens(),files.count(),storage.sumUsedBytes(),
                documents.count(),aiRequests.countByStatusAndCreatedAtGreaterThanEqual(AiRequestStatus.FAILED,today),
                providers.countByStatus(ProviderStatus.ENABLED),
                models.countByStatus(ModelStatus.ENABLED),
                hourlyTrend(now),
                audits.recent(RECENT_LIMIT),
                aiRequests.findByStatusOrderByCreatedAtDescIdDesc(AiRequestStatus.FAILED,
                                PageRequest.of(0,RECENT_LIMIT)).stream().map(AdminDtos::of).toList());
    }

    /**
     * Buckets the last {@value #TREND_HOURS} hours (UTC) by request volume and failures. Buckets are
     * returned oldest first and always {@value #TREND_HOURS} long, so an empty platform still yields
     * a flat chart instead of a misleading "no data" gap.
     */
    private List<AdminDtos.HourlyPoint> hourlyTrend(Instant now) {
        Instant currentHour=now.truncatedTo(ChronoUnit.HOURS);
        Instant windowStart=currentHour.minus(Duration.ofHours(TREND_HOURS-1));
        long[] totals=new long[TREND_HOURS];
        long[] failures=new long[TREND_HOURS];
        for (Object[] row : aiRequests.timestampsSince(windowStart)) {
            Instant created=(Instant) row[0];
            int bucket=(int) Duration.between(windowStart,created).toHours();
            if (bucket<0||bucket>=TREND_HOURS) continue;
            totals[bucket]++;
            if (row[1]==AiRequestStatus.FAILED) failures[bucket]++;
        }
        List<AdminDtos.HourlyPoint> points=new ArrayList<>(TREND_HOURS);
        for (int i=0;i<TREND_HOURS;i++) {
            points.add(new AdminDtos.HourlyPoint(windowStart.plus(Duration.ofHours(i)),totals[i],failures[i]));
        }
        return points;
    }
}
