package org.jeecg.modules.airag.collaboration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.collaboration")
public class CollaborationProperties {
    private boolean enabled = true;
    private long inboxIntervalMs = 500;
    private int inboxBatchSize = 50;
    private int inboxClaimSeconds = 30;
    private int inboxMaxRetries = 10;
    private int bindingTokenMinutes = 10;
    private String notificationStream = "ai:pipeline:notifications";
    private String notificationGroup = "jeecg-feishu-notifier";
    private int notificationPendingIdleSeconds = 60;
    private int notificationMaxRetries = 10;
    private long deliveryIntervalMs = 500;
    private int deliveryBatchSize = 50;
    private int deliveryClaimSeconds = 30;
    private String runDetailBaseUrl = "http://127.0.0.1:3100/multi-agent/runs";
}
