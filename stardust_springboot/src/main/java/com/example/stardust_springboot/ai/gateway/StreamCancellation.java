package com.example.stardust_springboot.ai.gateway;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class StreamCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Reason> reason = new AtomicReference<>();
    private final AtomicReference<Runnable> cancelAction = new AtomicReference<>(() -> { });

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
