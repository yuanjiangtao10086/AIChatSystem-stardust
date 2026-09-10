package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.ai.request.AiRequestLogRepository;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import com.example.stardust_springboot.usage.service.AiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AiStreamRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(AiStreamRecoveryService.class);
    private final ChatMessageRepository messageRepository;
    private final AiRequestLogRepository requestLogRepository;
    private final AiUsageService usageService;
    private final Clock clock;

    public AiStreamRecoveryService(ChatMessageRepository messageRepository,
                                   AiRequestLogRepository requestLogRepository,
                                   AiUsageService usageService,
                                   Clock clock) {
        this.messageRepository = messageRepository;
        this.requestLogRepository = requestLogRepository;
        this.usageService = usageService;
        this.clock = clock;
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedStreams() {
        Instant now = clock.instant();
        List<String> interrupted = requestLogRepository.findInterruptedRequestIds();
        int messages = messageRepository.failInterruptedStreams(now);
        int requests = requestLogRepository.failInterruptedRequests(now);
        int released = 0;
        for (String requestId : interrupted) {
            try {
                usageService.release(requestId);
                released++;
            } catch (RuntimeException error) {
                log.error("Failed to release AI usage reservation requestId={}", requestId, error);
            }
        }
        if (messages > 0 || requests > 0 || released > 0) {
            log.warn("Recovered interrupted AI streams messages={} requests={} releasedReservations={}",
                    messages, requests, released);
        }
    }
}
