package org.jeecg.modules.airag.execution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.executor")
public class AiExecutorProperties {
    private boolean enabled = true;
    private String gateway = "disabled";
    private int corePoolSize = 2;
    private int maxPoolSize = 5;
    private int queueCapacity = 100;
    private String taskStream = "ai:pipeline:tasks";
    private String taskGroup = "jeecg-agent-executor";
    private String notificationStream = "ai:pipeline:notifications";
    private long outboxIntervalMs = 1000;
    private int outboxBatchSize = 100;
    private int outboxClaimSeconds = 30;
    private int pendingScanSeconds = 30;
    private int pendingIdleSeconds = 60;
    private int pendingClaimBatchSize = 20;
    private int leaseScanSeconds = 30;
    private int dependencyScanSeconds = 30;
    private int maxDispatchRecoveries = 5;
    private int connectorMaxRequestBytes = 1048576;
    private int connectorMaxResponseBytes = 1048576;
}
