package org.jeecg.modules.airag.agent.support;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class FeishuMessageEventHandoff {
    private final FeishuMessageEventProcessor processor;
    private final ThreadPoolExecutor executor;
    @Autowired
    public FeishuMessageEventHandoff(ObjectProvider<FeishuMessageEventProcessor> processorProvider) {
        this(processorProvider.getIfAvailable(), defaultExecutor());
    }

    FeishuMessageEventHandoff(FeishuMessageEventProcessor processor, ThreadPoolExecutor executor) {
        this.processor = processor == null ? message ->
                log.debug("Feishu message handed off with no module processor: botKey={}, messageId={}",
                        message.getBotKey(), message.getMessageId()) : processor;
        this.executor = executor;
    }

    public boolean submit(FeishuInboundMessage message) {
        try {
            executor.execute(() -> processSafely(message));
            return true;
        } catch (RejectedExecutionException e) {
            // Never run on the SDK callback thread when the bounded queue is full.
            log.warn("Feishu message handoff queue is full: botKey={}, messageId={}",
                    message.getBotKey(), message.getMessageId());
            return false;
        }
    }

    private void processSafely(FeishuInboundMessage message) {
        try {
            processor.process(message);
        } catch (Exception e) {
            log.error("Feishu message processor failed: botKey={}, messageId={}, reason={}",
                    message.getBotKey(), message.getMessageId(), e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public void destroy() {
        executor.shutdownNow();
    }

    private static ThreadPoolExecutor defaultExecutor() {
        return new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
                new CustomizableThreadFactory("feishu-message-handoff-"), new ThreadPoolExecutor.AbortPolicy());
    }
}
