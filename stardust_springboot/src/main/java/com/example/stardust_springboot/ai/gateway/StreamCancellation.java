package com.example.stardust_springboot.ai.gateway;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class StreamCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Reason> reason = new AtomicReference<>();
    private final AtomicReference<Runnable> cancelAction = new AtomicReference<>(() -> { });
    // Last time an upstream event was observed. Used by the idle watchdog so that a stream
    // which keeps producing tokens (e.g. a reasoning model thinking for a long time) is never
    // killed for merely being slow, while a genuinely stalled stream still times out.
    private final AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());

    public boolean cancel() {
        return cancel(Reason.CANCELLED);
    }

    public boolean timeout() {
        return cancel(Reason.TIMEOUT);
    }

    private boolean cancel(Reason requestedReason) {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        reason.set(requestedReason);
        cancelAction.get().run();
        return true;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Record that an upstream event arrived; resets the idle timer. */
    public void markActivity() {
        lastActivityNanos.set(System.nanoTime());
    }

    /** Milliseconds since the last upstream event was observed. */
    public long idleMillis() {
        return Duration.ofNanos(System.nanoTime() - lastActivityNanos.get()).toMillis();
    }

    public boolean isTimedOut() {
        return reason.get() == Reason.TIMEOUT;
    }

    public void onCancel(Runnable action) {
        cancelAction.set(action);
        if (cancelled.get()) {
            action.run();
        }
    }

    private enum Reason {
        CANCELLED,
        TIMEOUT
    }
}
