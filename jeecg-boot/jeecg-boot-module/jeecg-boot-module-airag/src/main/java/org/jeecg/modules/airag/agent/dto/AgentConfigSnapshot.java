package org.jeecg.modules.airag.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.util.Map;

@Value
@Builder
public class AgentConfigSnapshot implements Serializable {
    String agentId;
    String agentCode;
    String name;
    String systemPrompt;
    Integer timeoutSeconds;
    Integer maxRetry;
    ConnectorSnapshot connector;
    FeishuBotSnapshot feishuBot;

    @Value
    @Builder
    public static class ConnectorSnapshot implements Serializable {
        String connectorId;
        String connectorCode;
        String baseUrl;
        String path;
        String authType;
        String authHeader;
        Map<String, String> requestHeaders;
        AiConfigDtos.ResponseMapping responseMapping;
        Integer connectTimeout;
        Integer readTimeout;
        boolean secretConfigured;
    }

    @Value
    @Builder
    public static class FeishuBotSnapshot implements Serializable {
        String botId;
        String botKey;
        String defaultChatId;
        boolean credentialsConfigured;
    }
}
