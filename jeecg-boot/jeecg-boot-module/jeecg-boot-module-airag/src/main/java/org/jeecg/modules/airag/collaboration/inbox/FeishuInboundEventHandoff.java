package org.jeecg.modules.airag.collaboration.inbox;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

@Component
public class FeishuInboundEventHandoff {
    private final FeishuInboxScheduler scheduler;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200), new CustomizableThreadFactory("feishu-inbox-handoff-"),
            new ThreadPoolExecutor.AbortPolicy());

    public FeishuInboundEventHandoff(FeishuInboxScheduler scheduler) { this.scheduler = scheduler; }

    public boolean submit(String inboundEventId) {
        try { executor.execute(() -> scheduler.processNow(inboundEventId)); return true; }
        catch (RejectedExecutionException full) { return false; }
    }

    @PreDestroy
    public void destroy() { executor.shutdownNow(); }
}
