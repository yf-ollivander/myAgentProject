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
    private String resultContractVersion;
    // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】持久化模型 Provider 配置并兼容 Custom-----------
    private String providerType;
    private String modelName;
    private String modelOptions;
    private String modelResponseMode;
    // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】持久化模型 Provider 配置并兼容 Custom-----------
    private String secretCipher;
    private Integer connectTimeout;
    private Integer readTimeout;
    private Boolean enabled;
    private String lastTestStatus;
    private String lastTestMessage;
    private Date lastTestTime;
    private Long lastTestDurationMs;
}
