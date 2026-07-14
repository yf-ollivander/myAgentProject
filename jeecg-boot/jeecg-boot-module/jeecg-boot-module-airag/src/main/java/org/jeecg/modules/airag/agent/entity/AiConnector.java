package org.jeecg.modules.airag.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_connector")
public class AiConnector extends AbstractAiConfigEntity {
    private String connectorCode;
    private String name;
    private String baseUrl;
    private String path;
    private String authType;
    private String authHeader;
    private String requestHeaders;
    private String responseMapping;
    private String secretCipher;
    private Integer connectTimeout;
    private Integer readTimeout;
    private Boolean enabled;
    private String lastTestStatus;
    private String lastTestMessage;
    private Date lastTestTime;
    private Long lastTestDurationMs;
}
