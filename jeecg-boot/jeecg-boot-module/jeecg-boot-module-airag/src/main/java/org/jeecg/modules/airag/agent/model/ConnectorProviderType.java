package org.jeecg.modules.airag.agent.model;

import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】集中维护 Provider 协议预设和路径约束-----------
public enum ConnectorProviderType {
    OPENAI_COMPATIBLE(true, "https://api.openai.com", "/v1/chat/completions", "BEARER", null),
    DEEPSEEK(true, "https://api.deepseek.com", "/chat/completions", "BEARER", null),
    ANTHROPIC(true, "https://api.anthropic.com", "/v1/messages", "API_KEY", "x-api-key"),
    GEMINI(true, "https://generativelanguage.googleapis.com", "/v1beta/models/{model}:generateContent", "API_KEY", "x-goog-api-key"),
    OLLAMA(true, "http://127.0.0.1:11434", "/api/chat", "NONE", null),
    CUSTOM(false, null, null, null, null);

    private final boolean modelProvider;
    private final String defaultBaseUrl;
    private final String defaultPath;
    private final String defaultAuthType;
    private final String defaultAuthHeader;

    ConnectorProviderType(boolean modelProvider, String defaultBaseUrl, String defaultPath,
                          String defaultAuthType, String defaultAuthHeader) {
        this.modelProvider = modelProvider;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultPath = defaultPath;
        this.defaultAuthType = defaultAuthType;
        this.defaultAuthHeader = defaultAuthHeader;
    }

    public boolean isModelProvider() {
        return modelProvider;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getDefaultPath() {
        return defaultPath;
    }

    public String getDefaultAuthType() {
        return defaultAuthType;
    }

    public String getDefaultAuthHeader() {
        return defaultAuthHeader;
    }

    public String resolvePath(String configuredPath, String modelName) {
        if (this != GEMINI) {
            if (configuredPath.contains("{") || configuredPath.contains("}")) {
                throw new IllegalArgumentException("Connector path templates are supported only for Gemini");
            }
            return configuredPath;
        }
        if (!configuredPath.equals(defaultPath) || !StringUtils.hasText(modelName)) {
            throw new IllegalArgumentException("Gemini path must use the fixed {model} template and a model name");
        }
        String encoded = URLEncoder.encode(modelName.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        return configuredPath.replace("{model}", encoded);
    }

    public static ConnectorProviderType fromNullable(String value) {
        return StringUtils.hasText(value) ? valueOf(value.trim()) : CUSTOM;
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】集中维护 Provider 协议预设和路径约束-----------
