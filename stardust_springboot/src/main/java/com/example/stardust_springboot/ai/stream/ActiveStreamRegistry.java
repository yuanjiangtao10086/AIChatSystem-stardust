package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.ai.gateway.StreamCancellation;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;

@Component
public class ActiveStreamRegistry {
    private final ConcurrentMap<String, ActiveStream> streams = new ConcurrentHashMap<>();

    public void register(String requestId, Long userId, StreamCancellation cancellation) {
        if (streams.putIfAbsent(requestId, new ActiveStream(userId, cancellation)) != null) {
            throw new IllegalStateException("Duplicate active AI request id");
        }
    }

    public CancelResult cancelOwned(String requestId, Long userId) {
        ActiveStream stream = streams.get(requestId);
        if (stream == null) {
            return CancelResult.NOT_ACTIVE;
        }
        if (!stream.userId().equals(userId)) {
            return CancelResult.NOT_OWNED;
        }
        boolean changed = stream.cancellation().cancel();
        try {
            stream.completed().get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // The endpoint remains bounded; the worker still owns eventual terminal persistence.
        }
        return changed ? CancelResult.CANCELLED : CancelResult.ALREADY_CANCELLED;
    }

    public void remove(String requestId) {
        ActiveStream stream = streams.remove(requestId);
        if (stream != null) {
            stream.completed().complete(null);
        }
    }

    @PreDestroy
    void cancelAll() {
        streams.values().forEach(stream -> stream.cancellation().cancel());
    }

    public enum CancelResult {
        CANCELLED,
        ALREADY_CANCELLED,
        NOT_ACTIVE,
        NOT_OWNED
    }

    private record ActiveStream(Long userId, StreamCancellation cancellation,
                                CompletableFuture<Void> completed) {
        private ActiveStream(Long userId, StreamCancellation cancellation) {
            this(userId, cancellation, new CompletableFuture<>());
        }
    }
}
