package org.jeecg.modules.airag.agent.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.service.AgentConnectorInvoker;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpAgentConnectorInvoker implements AgentConnectorInvoker {
    private static final int PREVIEW_LIMIT = 500;
    private final ObjectMapper objectMapper;
    private final SecretCipherService secretCipherService;
    private final ConnectorUriPolicy uriPolicy;

    public HttpAgentConnectorInvoker(ObjectMapper objectMapper, SecretCipherService secretCipherService,
                                     ConnectorUriPolicy uriPolicy) {
        this.objectMapper = objectMapper;
        this.secretCipherService = secretCipherService;
        this.uriPolicy = uriPolicy;
    }

    @Override
    public AiConfigDtos.AgentExecutionResult execute(AiAgent agent, AiConnector connector, JsonNode input) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("requestId", UUID.randomUUID().toString());
        payload.put("agentCode", agent.getAgentCode());
        payload.put("systemPrompt", agent.getSystemPrompt());
        payload.set("input", input == null ? NullNode.getInstance() : input);
        RawResponse raw = send(connector, payload);
        AiConfigDtos.AgentExecutionResult result = new AiConfigDtos.AgentExecutionResult();
        if (raw.errorMessage != null) {
            result.setSuccess(false);
            result.setErrorCode(raw.errorCode);
            result.setErrorMessage(raw.errorMessage);
            return result;
        }
        try {
            // Remote services may echo request headers; sanitize before any mapped output can reach an API response.
            JsonNode root = objectMapper.readTree(redact(raw.body, connector));
            AiConfigDtos.ResponseMapping mapping = readMapping(connector);
            JsonNode successNode = root.at(mapping.getSuccessPointer());
            result.setSuccess(successNode.isBoolean() && successNode.booleanValue());
            JsonNode output = root.at(mapping.getOutputPointer());
            result.setOutput(output.isMissingNode() ? NullNode.getInstance() : output);
            JsonNode summary = root.at(mapping.getSummaryPointer());
            result.setSummary(summary.isValueNode() ? summary.asText() : null);
            if (!result.isSuccess()) {
                result.setErrorCode("AGENT_REJECTED");
                result.setErrorMessage("Agent endpoint returned an unsuccessful result");
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorCode("INVALID_JSON");
            result.setErrorMessage("Agent endpoint returned invalid JSON");
        }
        return result;
    }

    @Override
    public AiConfigDtos.ConnectionTestResult test(AiConnector connector, JsonNode input) {
        long started = System.currentTimeMillis();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("requestId", UUID.randomUUID().toString());
        payload.set("input", input == null ? NullNode.getInstance() : input);
        RawResponse raw = send(connector, payload);
        AiConfigDtos.ConnectionTestResult result = new AiConfigDtos.ConnectionTestResult();
        result.setTestedAt(new Date());
        result.setDurationMs(System.currentTimeMillis() - started);
        result.setHttpStatus(raw.status);
        result.setSuccess(raw.errorMessage == null);
        result.setMessage(raw.errorMessage == null ? "Connection succeeded" : raw.errorMessage);
        result.setOutputPreview(raw.body == null ? null : abbreviate(redact(raw.body, connector)));
        if (raw.errorMessage == null) {
            try {
                JsonNode root = objectMapper.readTree(raw.body);
                AiConfigDtos.ResponseMapping mapping = readMapping(connector);
                JsonNode success = root.at(mapping.getSuccessPointer());
                if (!success.isBoolean() || root.at(mapping.getOutputPointer()).isMissingNode()
                        || root.at(mapping.getSummaryPointer()).isMissingNode()) {
                    result.setSuccess(false);
                    result.setMessage("Agent endpoint JSON does not match the configured response mapping");
                } else if (!success.booleanValue()) {
                    result.setSuccess(false);
                    result.setMessage("Agent endpoint returned a valid but unsuccessful result");
                }
            } catch (Exception e) {
                result.setSuccess(false);
                result.setMessage("Agent endpoint returned invalid JSON");
            }
        }
        return result;
    }

    private RawResponse send(AiConnector connector, JsonNode payload) {
        URI uri = uriPolicy.resolve(connector.getBaseUrl(), connector.getPath());
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connector.getConnectTimeout()))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(connector.getReadTimeout()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8));
            applyHeaders(builder, connector);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return RawResponse.error(response.statusCode(), "HTTP_" + response.statusCode(),
                        "Agent endpoint returned HTTP " + response.statusCode(), response.body());
            }
            return RawResponse.success(response.statusCode(), response.body());
        } catch (HttpTimeoutException e) {
            return RawResponse.error(null, "TIMEOUT", "Agent endpoint timed out", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RawResponse.error(null, "INTERRUPTED", "Agent endpoint call was interrupted", null);
        } catch (Exception e) {
            return RawResponse.error(null, "CONNECTION_ERROR", "Agent endpoint connection failed", null);
        }
    }

    private void applyHeaders(HttpRequest.Builder builder, AiConnector connector) throws Exception {
        Map<String, String> customHeaders = StringUtils.hasText(connector.getRequestHeaders())
                ? objectMapper.readValue(connector.getRequestHeaders(), new TypeReference<>() {})
                : new LinkedHashMap<>();
        customHeaders.forEach(builder::header);
        if ("BEARER".equals(connector.getAuthType())) {
            builder.header("Authorization", "Bearer " + secretCipherService.decrypt(connector.getSecretCipher()));
        } else if ("API_KEY".equals(connector.getAuthType())) {
            builder.header(connector.getAuthHeader(), secretCipherService.decrypt(connector.getSecretCipher()));
        }
    }

    private AiConfigDtos.ResponseMapping readMapping(AiConnector connector) throws Exception {
        return objectMapper.readValue(connector.getResponseMapping(), AiConfigDtos.ResponseMapping.class);
    }

    private String abbreviate(String body) {
        String normalized = body.replaceAll("[\\r\\n\\t]+", " ");
        return normalized.length() <= PREVIEW_LIMIT ? normalized : normalized.substring(0, PREVIEW_LIMIT) + "...";
    }

    private String redact(String body, AiConnector connector) {
        if (!StringUtils.hasText(connector.getSecretCipher())) {
            return body;
        }
        try {
            String secret = secretCipherService.decrypt(connector.getSecretCipher());
            return StringUtils.hasText(secret) ? body.replace(secret, "***") : body;
        } catch (Exception ignored) {
            return body;
        }
    }

    private static final class RawResponse {
        private final Integer status;
        private final String body;
        private final String errorCode;
        private final String errorMessage;

        private RawResponse(Integer status, String body, String errorCode, String errorMessage) {
            this.status = status;
            this.body = body;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        static RawResponse success(Integer status, String body) {
            return new RawResponse(status, body, null, null);
        }

        static RawResponse error(Integer status, String code, String message, String body) {
            return new RawResponse(status, body, code, message);
        }
    }
}
