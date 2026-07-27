package org.jeecg.modules.airag.execution.redis;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class RedisTaskGroupManager {
    private static final long RETRY_MILLIS = 5_000L;
    private static final long LOG_INTERVAL_MILLIS = 30_000L;
    private final StringRedisTemplate redis;
    private final AiExecutorProperties properties;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicLong nextAttemptAt = new AtomicLong(0L);
    private final AtomicLong nextLogAt = new AtomicLong(0L);

    public RedisTaskGroupManager(StringRedisTemplate redis, AiExecutorProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        ensureGroup();
    }

    public boolean ensureGroup() {
        if (!properties.isEnabled()) return false;
        if (ready.get()) return true;
        long now = System.currentTimeMillis();
        if (now < nextAttemptAt.get()) return false;
        synchronized (this) {
            if (ready.get()) return true;
            now = System.currentTimeMillis();
            if (now < nextAttemptAt.get()) return false;
            try {
                redis.execute((RedisCallback<Object>) connection -> connection.execute("XGROUP",
                        bytes("CREATE"), bytes(properties.getTaskStream()), bytes(properties.getTaskGroup()),
                        bytes("0-0"), bytes("MKSTREAM")));
                ready.set(true);
                nextAttemptAt.set(0L);
                return true;
            } catch (Exception exception) {
                if (containsMessage(exception, "BUSYGROUP")) {
                    ready.set(true);
                    nextAttemptAt.set(0L);
                    return true;
                }
                ready.set(false);
                nextAttemptAt.set(now + RETRY_MILLIS);
                logFailure(exception);
                return false;
            }
        }
    }

    public void onOperationFailure(Throwable failure) {
        if (containsMessage(failure, "NOGROUP")) {
            // Redis can lose a group while the application remains alive; the next poll must recreate it.
            ready.set(false);
            nextAttemptAt.set(0L);
        }
        logFailure(failure);
    }

    private void logFailure(Throwable failure) {
        long now = System.currentTimeMillis();
        long allowedAt = nextLogAt.get();
        if (now >= allowedAt && nextLogAt.compareAndSet(allowedAt, now + LOG_INTERVAL_MILLIS)) {
            log.warn("Redis task group operation failed: stream={}, group={}, errorType={}",
                    properties.getTaskStream(), properties.getTaskGroup(), failure.getClass().getSimpleName());
        }
    }

    private boolean containsMessage(Throwable failure, String marker) {
        Throwable current = failure;
        String expected = marker.toUpperCase(Locale.ROOT);
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().toUpperCase(Locale.ROOT).contains(expected)) return true;
            current = current.getCause();
        }
        return false;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
