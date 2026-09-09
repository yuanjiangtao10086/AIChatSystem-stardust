package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.ai.request.AiRequestLogRepository;
import com.example.stardust_springboot.conversation.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AiStreamRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(AiStreamRecoveryService.class);
    private final ChatMessageRepository messageRepository;
    private final AiRequestLogRepository requestLogRepository;
    private final Clock clock;

    public AiStreamRecoveryService(ChatMessageRepository messageRepository,
                                   AiRequestLogRepository requestLogRepository, Clock clock) {
        this.messageRepository = messageRepository;
        this.requestLogRepository = requestLogRepository;
        this.clock = clock;
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedStreams() {
        Instant now = clock.instant();
        int messages = messageRepository.failInterruptedStreams(now);
        int requests = requestLogRepository.failInterruptedRequests(now);
        if (messages > 0 || requests > 0) {
            log.warn("Recovered interrupted AI streams messages={} requests={}", messages, requests);
        }
    }
}
