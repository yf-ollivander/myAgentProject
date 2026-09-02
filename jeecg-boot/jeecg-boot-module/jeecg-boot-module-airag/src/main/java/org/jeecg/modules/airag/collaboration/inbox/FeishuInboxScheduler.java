package org.jeecg.modules.airag.collaboration.inbox;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.InboundStatus;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuInboundEvent;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuInboundEventMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;

@Slf4j
@Component
public class FeishuInboxScheduler {
    private static final int[] BACKOFF = {1, 2, 5, 10, 30};
    private static final String CLAIM_CANDIDATES_SQL = "SELECT id FROM ai_feishu_inbound_event WHERE ((status IN ('PENDING','FAILED') AND next_retry_at<=NOW(3)) OR (status='PROCESSING' AND claimed_until<NOW(3))) ORDER BY create_time LIMIT ?";
    private final AiFeishuInboundEventMapper mapper;
    private final FeishuInboundEventProcessor processor;
    private final CollaborationProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public FeishuInboxScheduler(AiFeishuInboundEventMapper mapper, FeishuInboundEventProcessor processor,
                                CollaborationProperties properties, JdbcTemplate jdbcTemplate) {
        this.mapper = mapper; this.processor = processor; this.properties = properties; this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${ai.collaboration.inbox-interval-ms:500}")
    public void poll() {
        if (!properties.isEnabled()) return;
        List<String> ids = selectClaimCandidates();
        if (ids.isEmpty()) return;
        String token = UUID.randomUUID().toString();
        mapper.claim(ids, token, properties.getInboxClaimSeconds());
        mapper.selectClaimed(token).forEach(event -> process(event, token));
    }

    // Keep only this empty-poll query outside MyBatis so StdOutImpl can still print useful business SQL.
    private List<String> selectClaimCandidates() {
        return jdbcTemplate.queryForList(CLAIM_CANDIDATES_SQL, String.class, properties.getInboxBatchSize());
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
            // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】记录安全事件元数据，定位 Inbox 重试但不泄露消息正文-----------
            log.warn("Feishu Inbox processing failed: inboundEventId={}, messageId={}, botId={}, attempt={}, nextStatus={}, errorType={}, reason={}",
                    event.getId(), event.getMessageId(), event.getBotId(), retry, status,
                    error.getClass().getSimpleName(), abbreviate(error.getMessage()).replace('\r', ' ').replace('\n', ' '));
            // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】记录安全事件元数据，定位 Inbox 重试但不泄露消息正文-----------
            mapper.markFailed(event.getId(), token, status, retry,
                    new Date(System.currentTimeMillis() + seconds * 1000L), abbreviate(error.getMessage()));
        }
    }

    private String abbreviate(String value) {
        if (value == null) return "Inbound processing failed";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
