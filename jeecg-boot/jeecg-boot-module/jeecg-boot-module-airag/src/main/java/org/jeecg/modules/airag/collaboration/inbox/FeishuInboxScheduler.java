package org.jeecg.modules.airag.collaboration.inbox;

import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.InboundStatus;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuInboundEvent;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuInboundEventMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FeishuInboxScheduler {
    private static final int[] BACKOFF = {1, 2, 5, 10, 30};
    private final AiFeishuInboundEventMapper mapper;
    private final FeishuInboundEventProcessor processor;
    private final CollaborationProperties properties;

    public FeishuInboxScheduler(AiFeishuInboundEventMapper mapper, FeishuInboundEventProcessor processor,
                                CollaborationProperties properties) {
        this.mapper = mapper; this.processor = processor; this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ai.collaboration.inbox-interval-ms:500}")
    public void poll() {
        if (!properties.isEnabled()) return;
        List<String> ids = mapper.selectClaimCandidates(properties.getInboxBatchSize());
        if (ids.isEmpty()) return;
        String token = UUID.randomUUID().toString();
        mapper.claim(ids, token, properties.getInboxClaimSeconds());
        mapper.selectClaimed(token).forEach(event -> process(event, token));
    }

    public void processNow(String id) {
        String token = UUID.randomUUID().toString();
        if (mapper.claimOne(id, token, properties.getInboxClaimSeconds()) == 1) {
            AiFeishuInboundEvent event = mapper.selectById(id);
            if (event != null) process(event, token);
        }
    }

    private void process(AiFeishuInboundEvent event, String token) {
        try {
            processor.process(event.getId());
            mapper.markProcessed(event.getId(), token, null, null, null);
        } catch (Exception error) {
            int retry = event.getRetryCount() + 1;
            String status = retry >= properties.getInboxMaxRetries() ? InboundStatus.DEAD.name() : InboundStatus.FAILED.name();
            int seconds = BACKOFF[Math.min(retry - 1, BACKOFF.length - 1)];
            mapper.markFailed(event.getId(), token, status, retry,
                    new Date(System.currentTimeMillis() + seconds * 1000L), abbreviate(error.getMessage()));
        }
    }

    private String abbreviate(String value) {
        if (value == null) return "Inbound processing failed";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
