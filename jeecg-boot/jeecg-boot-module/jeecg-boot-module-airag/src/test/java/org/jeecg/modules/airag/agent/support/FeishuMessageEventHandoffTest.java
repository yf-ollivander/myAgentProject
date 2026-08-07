package org.jeecg.modules.airag.agent.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeishuMessageEventHandoffTest {
    @Test
    void returnsImmediatelyWhenProcessorIsSlow() throws Exception {
        CountDownLatch processed = new CountDownLatch(1);
        FeishuMessageEventHandoff handoff = new FeishuMessageEventHandoff(message -> {
            try {
                Thread.sleep(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                processed.countDown();
            }
        }, executor(1));
        try {
            assertTimeoutPreemptively(Duration.ofMillis(100), () -> assertTrue(handoff.submit(message("m1"))));
            assertTrue(processed.await(1, TimeUnit.SECONDS));
        } finally {
            handoff.destroy();
        }
    }

    @Test
    void rejectsWithoutRunningOnCallerWhenQueueIsFull() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        FeishuMessageEventHandoff handoff = new FeishuMessageEventHandoff(message -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, executor(1));
        try {
            assertTrue(handoff.submit(message("m1")));
            assertTrue(handoff.submit(message("m2")));
            assertFalse(handoff.submit(message("m3")));
        } finally {
            release.countDown();
            handoff.destroy();
        }
    }

    private static ThreadPoolExecutor executor(int queueCapacity) {
        return new ThreadPoolExecutor(1, 1, 10L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), new ThreadPoolExecutor.AbortPolicy());
    }

    private static FeishuInboundMessage message(String id) {
        return FeishuInboundMessage.builder().botKey("testBot").messageId(id).build();
    }
}
