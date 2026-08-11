package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.execution.gateway.AgentExecutionGateway.AgentExecutionRequest;
import org.jeecg.modules.airag.pipeline.contract.AgentResultContract;
import org.jeecg.modules.airag.pipeline.contract.ArtifactDescriptor;
import org.jeecg.modules.airag.pipeline.contract.PipelineEnums;
import org.jeecg.modules.airag.pipeline.validation.AgentResultValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】统一 Custom 与模型 Provider 的测试和正式调用-----------
@Service
public class ConnectorInvocationService {
    private static final int PREVIEW_LIMIT = 500;
    private static final int SUMMARY_LIMIT = 1000;
    private static final String RESULT_INSTRUCTION = "Return only one strict AgentResultContract 1.1 JSON object. "
            + "Do not wrap it in Markdown. Required fields are contractVersion, status, artifacts, needsUser, and retryable.";
    private final ObjectMapper mapper;
    private final ObjectMapper strictMapper;
    private final ConnectorHttpTransport transport;
    private final ModelCallService modelCallService;
    private final AgentResultValidator resultValidator;
    // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】清除外部响应中回显的真实凭据-----------
    private final SecretCipherService cipher;
    // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】清除外部响应中回显的真实凭据-----------

    public ConnectorInvocationService(ObjectMapper mapper, ConnectorHttpTransport transport,
                                      ModelCallService modelCallService, AgentResultValidator resultValidator,
                                      SecretCipherService cipher) {
        this.mapper = mapper;
        this.strictMapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.transport = transport;
        this.modelCallService = modelCallService;
        this.resultValidator = resultValidator;
        // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】保存解密服务仅用于内存脱敏-----------
        this.cipher = cipher;
        // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】保存解密服务仅用于内存脱敏-----------
    }

    public AgentResultContract execute(AgentExecutionRequest request, AiConnector current) {
        ConnectorRuntimeConfig config = withProviderHeaders(
                ConnectorRuntimeConfig.fromSnapshot(request.agentSnapshot().getConnector(), current, mapper));
        if (config.getProviderType().isModelProvider()) {
            return invokeModel(request.invocationId(), request.agentSnapshot().getSystemPrompt(), request.input(),
                    request.resumeInput(), request.artifactInputs(), config);
        }
        return invokeCustomResult11(request, config);
    }

    public AiConfigDtos.AgentExecutionResult execute(AiAgent agent, AiConnector connector, JsonNode input) {
        ConnectorRuntimeConfig config = withProviderHeaders(ConnectorRuntimeConfig.fromEntity(connector, mapper));
        try {
            AgentResultContract result;
            if (config.getProviderType().isModelProvider()) {
                result = invokeModel(UUID.randomUUID().toString(), agent.getSystemPrompt(), input, null, List.of(), config);
            } else if (AiConfigDtos.CONTRACT_1_1.equals(config.getResultContractVersion())) {
                result = invokeCustomAgentResult11(agent, input, config);
            } else {
                return invokeLegacyAgent(agent, input, config);
            }
            return toAgentExecutionResult(result);
        } catch (ConnectorCallException failure) {
            return failedExecution(failure.getErrorCode(), failure.getMessage());
        }
    }

    public AiConfigDtos.ConnectionTestResult test(AiConnector connector, JsonNode input) {
        long started = System.currentTimeMillis();
        AiConfigDtos.ConnectionTestResult result = new AiConfigDtos.ConnectionTestResult();
        result.setTestedAt(new Date());
        ConnectorRuntimeConfig config;
        try {
            config = withProviderHeaders(ConnectorRuntimeConfig.fromEntity(connector, mapper));
            if (config.getProviderType().isModelProvider()) {
                AgentResultContract modelResult = invokeModel(UUID.randomUUID().toString(), null, input, null, List.of(), config);
                result.setOutputPreview(abbreviate(modelResult.getSummary()));
            } else if (AiConfigDtos.CONTRACT_1_1.equals(config.getResultContractVersion())) {
                AgentResultContract customResult = invokeCustomProbe(input, config);
                result.setOutputPreview(abbreviate(customResult.getSummary()));
            } else {
                ConnectorHttpResponse response = send(config, legacyProbe(input));
                result.setHttpStatus(response.statusCode());
                JsonNode root;
                try {
                    root = mapper.readTree(redact(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8), config));
                    validateLegacyResponse(root, config.getResponseMapping());
                } catch (ConnectorCallException invalid) {
                    throw new ConnectorCallException(invalid.getErrorCode(), invalid.getMessage(),
                            invalid.isRetryable(), response.statusCode());
                } catch (Exception invalid) {
                    throw new ConnectorCallException("INVALID_JSON", "Agent endpoint returned invalid JSON",
                            false, response.statusCode());
                }
                result.setOutputPreview(abbreviate(mapper.writeValueAsString(root)));
            }
            result.setSuccess(true);
            result.setMessage("Connection succeeded");
        } catch (ConnectorCallException failure) {
            result.setSuccess(false);
            result.setHttpStatus(failure.getHttpStatus());
            result.setMessage(failure.getMessage());
        } catch (Exception invalid) {
            result.setSuccess(false);
            result.setMessage("Connector response is invalid");
        }
        result.setDurationMs(System.currentTimeMillis() - started);
        return result;
    }

    private AgentResultContract invokeModel(String requestId, String systemPrompt, JsonNode input,
                                            JsonNode resumeInput, List<ArtifactDescriptor> artifacts,
                                            ConnectorRuntimeConfig config) {
        String prompt = renderPrompt(input, resumeInput, artifacts);
        String effectiveSystem = config.getResponseMode() == ModelResponseMode.RESULT_1_1
                ? appendInstruction(systemPrompt) : systemPrompt;
        ModelCallRequest request = ModelCallRequest.builder().requestId(requestId)
                .modelName(config.getModelName()).systemPrompt(effectiveSystem).userPrompt(prompt)
                .resumeInput(resumeInput).artifactInputs(artifacts == null ? List.of() : artifacts)
                .modelOptions(config.getModelOptions()).responseMode(config.getResponseMode()).build();
        ModelCallResponse response = modelCallService.invoke(request, config);
        if (!StringUtils.hasText(response.getText())) {
            throw ConnectorCallException.nonRetryable("MODEL_RESPONSE_EMPTY", "Model Provider returned empty text");
        }
        if (config.getResponseMode() == ModelResponseMode.TEXT) {
            return textResult(response.getText());
        }
        return strictResult(response.getText(), "MODEL_RESPONSE_INVALID");
    }

    private AgentResultContract invokeCustomResult11(AgentExecutionRequest request, ConnectorRuntimeConfig config) {
        ObjectNode payload = mapper.createObjectNode().put("contractVersion", "1.1")
                .put("requestId", request.invocationId()).put("runId", request.runId())
                .put("nodeRunId", request.nodeRunId()).put("attemptNo", request.attemptNo())
                .put("resumeGeneration", request.resumeGeneration()).put("agentCode", request.agentSnapshot().getAgentCode())
                .put("systemPrompt", request.agentSnapshot().getSystemPrompt()).put("traceId", request.traceId());
        payload.set("input", nullNode(request.input()));
        payload.set("resumeInput", nullNode(request.resumeInput()));
        payload.set("artifactInputs", mapper.valueToTree(request.artifactInputs()));
        return strictResult(sendText(config, payload), "CONNECTOR_RESPONSE_INVALID");
    }

    private AgentResultContract invokeCustomAgentResult11(AiAgent agent, JsonNode input, ConnectorRuntimeConfig config) {
        ObjectNode payload = mapper.createObjectNode().put("contractVersion", "1.1")
                .put("requestId", UUID.randomUUID().toString()).put("agentCode", agent.getAgentCode())
                .put("systemPrompt", agent.getSystemPrompt());
        payload.set("input", nullNode(input));
        payload.set("resumeInput", NullNode.getInstance());
        payload.set("artifactInputs", mapper.createArrayNode());
        return strictResult(sendText(config, payload), "CONNECTOR_RESPONSE_INVALID");
    }

    private AgentResultContract invokeCustomProbe(JsonNode input, ConnectorRuntimeConfig config) {
        ObjectNode payload = legacyProbe(input);
        payload.put("contractVersion", "1.1").put("agentCode", "connector-test");
        payload.set("resumeInput", NullNode.getInstance());
        payload.set("artifactInputs", mapper.createArrayNode());
        return strictResult(sendText(config, payload), "CONNECTOR_RESPONSE_INVALID");
    }

    private AiConfigDtos.AgentExecutionResult invokeLegacyAgent(AiAgent agent, JsonNode input,
                                                                ConnectorRuntimeConfig config) {
        ObjectNode payload = legacyProbe(input).put("agentCode", agent.getAgentCode())
                .put("systemPrompt", agent.getSystemPrompt());
        String body = redact(sendText(config, payload), config);
        try {
            JsonNode root = mapper.readTree(body);
            AiConfigDtos.ResponseMapping mapping = config.getResponseMapping();
            validateLegacyResponse(root, mapping);
            AiConfigDtos.AgentExecutionResult result = new AiConfigDtos.AgentExecutionResult();
            boolean success = root.at(mapping.getSuccessPointer()).booleanValue();
            result.setSuccess(success);
            result.setOutput(root.at(mapping.getOutputPointer()));
            JsonNode summary = root.at(mapping.getSummaryPointer());
            result.setSummary(summary.isValueNode() ? summary.asText() : null);
            if (!success) {
                result.setErrorCode("AGENT_REJECTED");
                result.setErrorMessage("Agent endpoint returned an unsuccessful result");
            }
            return result;
        } catch (ConnectorCallException failure) {
            throw failure;
        } catch (Exception invalid) {
            throw ConnectorCallException.nonRetryable("INVALID_JSON", "Agent endpoint returned invalid JSON");
        }
    }

    private AgentResultContract strictResult(String body, String invalidCode) {
        try {
            AgentResultContract result = strictMapper.readValue(body, AgentResultContract.class);
            List<String> errors = resultValidator.validate(result);
            if (!errors.isEmpty()) {
                throw ConnectorCallException.nonRetryable("AGENT_RESULT_INVALID", "Agent result contract is invalid");
            }
            return result;
        } catch (ConnectorCallException failure) {
            throw failure;
        } catch (Exception invalid) {
            throw ConnectorCallException.nonRetryable(invalidCode, "Agent result response is invalid");
        }
    }

    private AgentResultContract textResult(String text) {
        String normalized = text.trim();
        AgentResultContract result = new AgentResultContract();
        result.setContractVersion("1.1");
        result.setStatus(PipelineEnums.AgentResultStatus.SUCCESS);
        result.setSummary(abbreviate(normalized, SUMMARY_LIMIT));
        result.setOutput(mapper.createObjectNode().put("text", normalized));
        result.setArtifacts(new ArrayList<>());
        result.setNeedsUser(false);
        result.setRetryable(false);
        // update-begin---author:Codex ---date:2026-08-10  for:【REQ-HTTP-MODEL-20260810】TEXT 转换结果也必须通过统一 Result 1.1 边界校验-----------
        if (!resultValidator.validate(result).isEmpty()) {
            throw ConnectorCallException.nonRetryable("AGENT_RESULT_INVALID", "Converted model result is invalid");
        }
        // update-end---author:Codex ---date:2026-08-10  for:【REQ-HTTP-MODEL-20260810】TEXT 转换结果也必须通过统一 Result 1.1 边界校验-----------
        return result;
    }

    private String renderPrompt(JsonNode input, JsonNode resumeInput, List<ArtifactDescriptor> artifacts) {
        String primary = input != null && input.isObject() && input.path("prompt").isTextual()
                ? input.path("prompt").textValue() : stableJson(nullNode(input));
        StringBuilder prompt = new StringBuilder(primary);
        if (resumeInput != null && !resumeInput.isNull()) {
            prompt.append("\n\n[Resume Input]\n").append(stableJson(resumeInput));
        }
        if (artifacts != null && !artifacts.isEmpty()) {
            prompt.append("\n\n[Artifact Inputs]\n").append(stableJson(mapper.valueToTree(artifacts)));
        }
        return prompt.toString();
    }

    private String stableJson(JsonNode value) {
        try {
            return mapper.copy().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .writeValueAsString(sortNode(value));
        } catch (Exception invalid) {
            throw ConnectorCallException.nonRetryable("CONNECTOR_REQUEST_INVALID", "Model input is invalid");
        }
    }

    private JsonNode sortNode(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> sorted.set(name, sortNode(value.get(name))));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode sorted = mapper.createArrayNode();
            value.forEach(item -> sorted.add(sortNode(item)));
            return sorted;
        }
        return value;
    }

    private ConnectorRuntimeConfig withProviderHeaders(ConnectorRuntimeConfig config) {
        if (config.getProviderType() != ConnectorProviderType.ANTHROPIC) {
            return config;
        }
        Map<String, String> headers = new LinkedHashMap<>(config.getRequestHeaders());
        // update-begin---author:Codex ---date:2026-08-10  for:【REQ-HTTP-MODEL-20260810】保留用户以任意大小写配置的 Anthropic 版本 Header-----------
        if (headers.keySet().stream().noneMatch("anthropic-version"::equalsIgnoreCase)) {
            headers.put("anthropic-version", "2023-06-01");
        }
        // update-end---author:Codex ---date:2026-08-10  for:【REQ-HTTP-MODEL-20260810】保留用户以任意大小写配置的 Anthropic 版本 Header-----------
        return config.toBuilder().requestHeaders(headers).build();
    }

    private ConnectorHttpResponse send(ConnectorRuntimeConfig config, JsonNode payload) {
        try {
            return transport.postJson(config, mapper.writeValueAsBytes(payload));
        } catch (ConnectorCallException failure) {
            throw failure;
        } catch (Exception invalid) {
            throw ConnectorCallException.nonRetryable("CONNECTOR_REQUEST_INVALID", "Connector request is invalid");
        }
    }

    private String sendText(ConnectorRuntimeConfig config, JsonNode payload) {
        return new String(send(config, payload).body(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private ObjectNode legacyProbe(JsonNode input) {
        ObjectNode payload = mapper.createObjectNode().put("requestId", UUID.randomUUID().toString());
        payload.set("input", nullNode(input));
        return payload;
    }

    private void validateLegacyResponse(JsonNode root, AiConfigDtos.ResponseMapping mapping) {
        if (mapping == null || !root.at(mapping.getSuccessPointer()).isBoolean()
                || root.at(mapping.getOutputPointer()).isMissingNode()
                || root.at(mapping.getSummaryPointer()).isMissingNode()) {
            throw ConnectorCallException.nonRetryable("INVALID_JSON", "Agent endpoint JSON does not match the configured response mapping");
        }
        if (!root.at(mapping.getSuccessPointer()).booleanValue()) {
            throw ConnectorCallException.nonRetryable("AGENT_REJECTED", "Agent endpoint returned a valid but unsuccessful result");
        }
    }

    private AiConfigDtos.AgentExecutionResult toAgentExecutionResult(AgentResultContract contract) {
        AiConfigDtos.AgentExecutionResult result = new AiConfigDtos.AgentExecutionResult();
        boolean success = contract.getStatus() == PipelineEnums.AgentResultStatus.SUCCESS;
        result.setSuccess(success);
        result.setOutput(contract.getOutput());
        result.setSummary(contract.getSummary());
        if (!success) {
            result.setErrorCode(contract.getErrorCode() == null ? contract.getStatus().name() : contract.getErrorCode());
            result.setErrorMessage(contract.getErrorMessage() == null ? "Agent did not return SUCCESS" : contract.getErrorMessage());
        }
        return result;
    }

    private AiConfigDtos.AgentExecutionResult failedExecution(String code, String message) {
        AiConfigDtos.AgentExecutionResult result = new AiConfigDtos.AgentExecutionResult();
        result.setSuccess(false);
        result.setErrorCode(code);
        result.setErrorMessage(message);
        return result;
    }

    private JsonNode nullNode(JsonNode node) {
        return node == null ? NullNode.getInstance() : node;
    }

    private String appendInstruction(String systemPrompt) {
        return StringUtils.hasText(systemPrompt) ? systemPrompt.trim() + "\n\n" + RESULT_INSTRUCTION : RESULT_INSTRUCTION;
    }

    private String redact(String body, ConnectorRuntimeConfig config) {
        if (body == null) return null;
        String redacted = body;
        if (StringUtils.hasText(config.getSecretCipher())) {
            // Raw credentials are never persisted; response echoes are removed before JSON mapping or previews.
            String secret = cipher.decrypt(config.getSecretCipher());
            if (StringUtils.hasText(secret)) redacted = redacted.replace(secret, "***");
            redacted = redacted.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***");
        }
        for (String value : config.getRequestHeaders().values()) {
            if (StringUtils.hasText(value)) redacted = redacted.replace(value, "***");
        }
        return redacted;
    }

    private String abbreviate(String value) {
        return abbreviate(value, PREVIEW_LIMIT);
    }

    private String abbreviate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】统一 Custom 与模型 Provider 的测试和正式调用-----------
