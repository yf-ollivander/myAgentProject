package org.jeecg.modules.airag.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent")
public class AiAgent extends AbstractAiConfigEntity {
    private String agentCode;
    private String name;
    private String description;
    private String systemPrompt;
    private String connectorId;
    private String feishuBotId;
    private Integer timeoutSeconds;
    private Integer maxRetry;
    private Boolean enabled;
    private String lastTestStatus;
    private String lastTestMessage;
    private Date lastTestTime;
    private Long lastTestDurationMs;
}
