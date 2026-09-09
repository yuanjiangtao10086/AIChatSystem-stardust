package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.ai.request.*;
import com.example.stardust_springboot.conversation.repository.*;
import com.example.stardust_springboot.file.repository.*;
import com.example.stardust_springboot.knowledge.repository.KnowledgeDocumentRepository;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
public class AdminDashboardService {
    private final AppUserRepository users; private final ConversationRepository conversations;
    private final ChatMessageRepository messages; private final AiRequestLogRepository aiRequests;
    private final UserFileRepository files; private final UserStorageUsageRepository storage;
    private final KnowledgeDocumentRepository documents; private final Clock clock;
    public AdminDashboardService(AppUserRepository users, ConversationRepository conversations,
            ChatMessageRepository messages, AiRequestLogRepository aiRequests, UserFileRepository files,
            UserStorageUsageRepository storage, KnowledgeDocumentRepository documents, Clock clock) {
        this.users=users;this.conversations=conversations;this.messages=messages;this.aiRequests=aiRequests;
        this.files=files;this.storage=storage;this.documents=documents;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public AdminDtos.Dashboard dashboard(){
        Instant now=clock.instant(); Instant today=now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant activeSince=now.minus(Duration.ofDays(30));
        return new AdminDtos.Dashboard(users.count(),users.countByCreatedAtGreaterThanEqual(today),
                users.countByLastLoginAtGreaterThanEqualAndDeletedAtIsNull(activeSince),conversations.count(),
                messages.count(),aiRequests.count(),aiRequests.sumTotalTokens(),files.count(),storage.sumUsedBytes(),
                documents.count(),aiRequests.countByStatusAndCreatedAtGreaterThanEqual(AiRequestStatus.FAILED,today));
    }
}
