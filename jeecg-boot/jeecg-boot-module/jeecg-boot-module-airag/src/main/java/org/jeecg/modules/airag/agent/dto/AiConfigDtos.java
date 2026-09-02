package org.jeecg.modules.airag.agent.dto;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jeecg.common.api.vo.Result;

import java.util.Date;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AiConfigDtos {
    private AiConfigDtos() {
    }

    private static final String CODE_PATTERN = "^[A-Za-z][A-Za-z0-9_-]*$";
    public static final String DIRECT_AGENT = "DIRECT_AGENT";
    public static final String ORCHESTRATOR = "ORCHESTRATOR";
    public static final String CONTRACT_LEGACY = "LEGACY";
    public static final String CONTRACT_1_1 = "1.1";
    // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】固定 Provider 和模型结果模式公共值-----------
    public static final String PROVIDER_CUSTOM = "CUSTOM";
    public static final String RESPONSE_MODE_TEXT = "TEXT";
    public static final String RESPONSE_MODE_RESULT_1_1 = "RESULT_1_1";
    // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】固定 Provider 和模型结果模式公共值-----------

    @Data
    public static class AgentUpsertRequest {
        @NotBlank @Size(max = 64) @Pattern(regexp = CODE_PATTERN)
        private String agentCode;
        @NotBlank @Size(max = 100)
        private String name;
        @Size(max = 500)
        private String description;
        @JSONField(serialize = false)
        @ToString.Exclude
        @Size(max = 20000)
        private String systemPrompt;
        @NotBlank
        private String connectorId;
        private String feishuBotId;
        @NotNull @Min(1) @Max(300)
        private Integer timeoutSeconds = 300;
        @NotNull @Min(0) @Max(2)
        private Integer maxRetry = 0;
    }

    @Data
    public static class ConnectorUpsertRequest {
        @NotBlank @Size(max = 64) @Pattern(regexp = CODE_PATTERN)
        private String connectorCode;
        @NotBlank @Size(max = 100)
        private String name;
        @NotBlank @Size(max = 500)
        private String baseUrl;
        @NotBlank @Size(max = 255)
        private String path;
        @NotBlank @Pattern(regexp = "NONE|BEARER|API_KEY")
        private String authType = "NONE";
        @Size(max = 100)
        private String authHeader;
        private Map<String, String> requestHeaders = new LinkedHashMap<>();
        @Valid
        private ResponseMapping responseMapping = new ResponseMapping();
        @NotBlank @Pattern(regexp = "LEGACY|1\\.1")
        private String resultContractVersion = CONTRACT_LEGACY;
        // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】接收严格模型 Provider 配置-----------
        @NotBlank @Pattern(regexp = "OPENAI_COMPATIBLE|DEEPSEEK|ANTHROPIC|GEMINI|OLLAMA|CUSTOM")
        private String providerType = PROVIDER_CUSTOM;
        @Size(max = 128)
        private String modelName;
        @Valid
        private ModelOptions modelOptions = new ModelOptions();
        @NotBlank @Pattern(regexp = "TEXT|RESULT_1_1")
        private String modelResponseMode = RESPONSE_MODE_TEXT;
        // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】接收严格模型 Provider 配置-----------
        @JSONField(serialize = false)
        @ToString.Exclude
        @Size(max = 4000)
        private String secret;
        private boolean clearSecret;
        @NotNull @Min(1) @Max(300)
        private Integer connectTimeout = 10;
        @NotNull @Min(1) @Max(300)
        private Integer readTimeout = 300;
    }

    @Data
    public static class ResponseMapping {
        @NotBlank @Size(max = 255)
        private String successPointer = "/success";
        @NotBlank @Size(max = 255)
        private String outputPointer = "/output";
        @NotBlank @Size(max = 255)
        private String summaryPointer = "/summary";
    }

    // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】限制可透传的通用模型参数-----------
    @Data
    public static class ModelOptions implements Serializable {
        @DecimalMin("0.0") @DecimalMax("2.0")
        private BigDecimal temperature;
        @DecimalMin("0.0") @DecimalMax("1.0")
        private BigDecimal topP;
        @Min(1) @Max(65536)
        private Integer maxTokens;

        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown model option: " + name);
        }
    }
    // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】限制可透传的通用模型参数-----------

    @Data
    public static class FeishuBotUpsertRequest {
        @NotBlank @Size(max = 64) @Pattern(regexp = CODE_PATTERN)
        private String botKey;
        @NotBlank @Size(max = 100)
        private String name;
        @NotBlank @Size(max = 100)
        private String appId;
        @JSONField(serialize = false) @ToString.Exclude @Size(max = 4000)
        private String appSecret;
        @JSONField(serialize = false) @ToString.Exclude @Size(max = 4000)
        private String verificationToken;
        @JSONField(serialize = false) @ToString.Exclude @Size(max = 4000)
        private String encryptKey;
        @Size(max = 100)
        private String defaultChatId;
        @NotBlank @Pattern(regexp = "DIRECT_AGENT|ORCHESTRATOR")
        private String entryMode = DIRECT_AGENT;
        private Boolean commandEnabled = false;
        private boolean clearAppSecret;
        private boolean clearVerificationToken;
        private boolean clearEncryptKey;
    }

    @Data
    public static class TestInput {
        @JSONField(serialize = false)
        @ToString.Exclude
        // Codex 2026-08-10 REQ-HTTP-MODEL-20260810: Jackson 3 cannot bind HTTP JSON directly to a Jackson 2 JsonNode.
        private Object input;

        public JsonNode toInternalJson(ObjectMapper objectMapper) {
            return input == null ? null : objectMapper.valueToTree(input);
        }
    }

    @Data
    public static class FeishuTestInput {
        @JSONField(serialize = false)
        @ToString.Exclude
        @Size(max = 500)
        private String testMessage;
    }

    @Data
    public static class MockAgentRequest {
        private String requestId;
        private String agentCode;
        @JSONField(serialize = false)
        @ToString.Exclude
        private String systemPrompt;
        @JSONField(serialize = false)
        @ToString.Exclude
        private JsonNode input;
    }

    @Data
    public static class AgentView {
        private String id;
        private String agentCode;
        private String name;
        private String description;
        private String systemPrompt;
        private String connectorId;
        private String connectorName;
        private String feishuBotId;
        private String feishuBotName;
        private Integer timeoutSeconds;
        private Integer maxRetry;
        private Boolean enabled;
        private String lastTestStatus;
        private String lastTestMessage;
        private Date lastTestTime;
        private Long lastTestDurationMs;
        private String createBy;
        private Date createTime;
    }

    @Data
    public static class AgentOption {
        private String id;
        private String agentCode;
        private String name;
        private String description;
    }

    @Data
    public static class ConnectorView {
        private String id;
        private String connectorCode;
        private String name;
        private String baseUrl;
        private String path;
        private String authType;
        private String authHeader;
        private Map<String, String> requestHeaders;
        private ResponseMapping responseMapping;
        private String resultContractVersion;
        // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】返回模型 Provider 展示字段-----------
        private String providerType;
        private String modelName;
        private ModelOptions modelOptions;
        private String modelResponseMode;
        // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】返回模型 Provider 展示字段-----------
        private boolean secretConfigured;
        private Integer connectTimeout;
        private Integer readTimeout;
        private Boolean enabled;
        private String lastTestStatus;
        private String lastTestMessage;
        private Date lastTestTime;
        private Long lastTestDurationMs;
        private String createBy;
        private Date createTime;
    }

    @Data
    public static class FeishuBotView {
        private String id;
        private String botKey;
        private String name;
        private String appId;
        private boolean appSecretConfigured;
        private boolean verificationTokenConfigured;
        private boolean encryptKeyConfigured;
        private String defaultChatId;
        private String entryMode;
        private Boolean commandEnabled;
        private String callbackUrl;
        private String connectionMode;
        private String connectionStatus;
        private String eventHandlingStatus;
        private Boolean enabled;
        private String lastTestStatus;
        private String lastTestMessage;
        private Date lastTestTime;
        private Long lastTestDurationMs;
        private String createBy;
        private Date createTime;
    }

    @Data
    public static class ConnectionTestResult {
        private boolean success;
        private Date testedAt;
        private long durationMs;
        private Integer httpStatus;
        private String message;
        private String outputPreview;
    }

    @Data
    public static class AgentExecutionResult {
        private boolean success;
        private JsonNode output;
        private String summary;
        private String errorCode;
        private String errorMessage;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MockAgentResult extends Result<AgentExecutionResult> {
        private JsonNode output;
        private String summary;
    }
}
