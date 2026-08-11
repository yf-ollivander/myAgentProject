package org.jeecg.modules.airag.agent.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.util.Map;

@Value
@Builder
@JsonDeserialize(builder = AgentConfigSnapshot.AgentConfigSnapshotBuilder.class)
public class AgentConfigSnapshot implements Serializable {
    String agentId;
    String agentCode;
    String name;
    String systemPrompt;
    Integer timeoutSeconds;
    Integer maxRetry;
    ConnectorSnapshot connector;
    FeishuBotSnapshot feishuBot;

    @JsonPOJOBuilder(withPrefix = "")
    public static class AgentConfigSnapshotBuilder {}

    @Value
    @Builder
    @JsonDeserialize(builder = ConnectorSnapshot.ConnectorSnapshotBuilder.class)
    public static class ConnectorSnapshot implements Serializable {
        String connectorId;
        String connectorCode;
        String baseUrl;
        String path;
        String authType;
        String authHeader;
        Map<String, String> requestHeaders;
        AiConfigDtos.ResponseMapping responseMapping;
        String resultContractVersion;
        // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】冻结无密钥模型 Provider 配置-----------
        String providerType;
        String modelName;
        AiConfigDtos.ModelOptions modelOptions;
        String modelResponseMode;
        // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】冻结无密钥模型 Provider 配置-----------
        Integer connectTimeout;
        Integer readTimeout;
        boolean secretConfigured;

        @JsonPOJOBuilder(withPrefix = "")
        public static class ConnectorSnapshotBuilder {}
    }

    @Value
    @Builder
    @JsonDeserialize(builder = FeishuBotSnapshot.FeishuBotSnapshotBuilder.class)
    public static class FeishuBotSnapshot implements Serializable {
        String botId;
        String botKey;
        String defaultChatId;
        boolean credentialsConfigured;

        @JsonPOJOBuilder(withPrefix = "")
        public static class FeishuBotSnapshotBuilder {}
    }
}
