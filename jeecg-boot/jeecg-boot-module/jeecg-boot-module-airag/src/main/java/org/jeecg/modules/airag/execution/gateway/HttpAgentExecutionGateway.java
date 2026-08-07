package org.jeecg.modules.airag.execution.gateway;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.mapper.AiConnectorMapper;
import org.jeecg.modules.airag.agent.support.ConnectorUriPolicy;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.pipeline.contract.AgentResultContract;
import org.jeecg.modules.airag.pipeline.validation.AgentResultValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "ai.executor.gateway", havingValue = "http")
public class HttpAgentExecutionGateway implements AgentExecutionGateway {
    private final ObjectMapper mapper;
    private final AiConnectorMapper connectorMapper;
    private final SecretCipherService cipher;
    private final ConnectorUriPolicy uriPolicy;
    private final AiAgentProperties agentProperties;
    private final AiExecutorProperties executorProperties;
    private final AgentResultValidator validator;

    public HttpAgentExecutionGateway(ObjectMapper mapper, AiConnectorMapper connectorMapper,
                                     SecretCipherService cipher, ConnectorUriPolicy uriPolicy,
                                     AiAgentProperties agentProperties, AiExecutorProperties executorProperties,
                                     AgentResultValidator validator) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.connectorMapper = connectorMapper;
        this.cipher = cipher;
        this.uriPolicy = uriPolicy;
        this.agentProperties = agentProperties;
        this.executorProperties = executorProperties;
        this.validator = validator;
    }

    @Override
    public AgentResultContract execute(AgentExecutionRequest request) {
        AgentConfigSnapshot.ConnectorSnapshot snapshot = requireSnapshot(request);
        AiConnector current = requireCurrentConnector(snapshot.getConnectorId(), request.tenantId());
        URI endpoint = resolveEndpoint(snapshot);
        rejectUnsafeResolution(endpoint);
        byte[] body = serializeRequest(request);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(Math.max(1, snapshot.getReadTimeout())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (snapshot.getRequestHeaders() != null) snapshot.getRequestHeaders().forEach(builder::header);
        applyCredential(builder, snapshot, current);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(1, snapshot.getConnectTimeout())))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBody;
            try (InputStream stream = response.body()) {
                responseBody = readBounded(stream, executorProperties.getConnectorMaxResponseBytes());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() == 408 || response.statusCode() == 429
                        || response.statusCode() >= 500;
                throw new AgentExecutionException("HTTP_" + response.statusCode(),
                        "Agent endpoint returned HTTP " + response.statusCode(), retryable);
            }
            AgentResultContract result;
            try {
                result = mapper.readValue(responseBody, AgentResultContract.class);
            } catch (Exception invalid) {
                throw AgentExecutionException.nonRetryable("CONNECTOR_RESPONSE_INVALID", "Agent response is invalid");
            }
            List<String> errors = validator.validate(result);
            if (!errors.isEmpty()) {
                throw AgentExecutionException.nonRetryable("AGENT_RESULT_INVALID", "Agent result contract is invalid");
            }
            return result;
        } catch (AgentExecutionException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw AgentExecutionException.retryable("TIMEOUT", "Agent endpoint timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AgentExecutionException.nonRetryable("INTERRUPTED", "Agent request was interrupted");
        } catch (Exception e) {
            throw AgentExecutionException.retryable("CONNECTION_ERROR", "Agent endpoint connection failed");
        }
    }

    private AgentConfigSnapshot.ConnectorSnapshot requireSnapshot(AgentExecutionRequest request) {
        AgentConfigSnapshot snapshot = request.agentSnapshot();
        if (snapshot == null || snapshot.getConnector() == null
                || !AiConfigDtos.CONTRACT_1_1.equals(snapshot.getConnector().getResultContractVersion())) {
            throw AgentExecutionException.nonRetryable("CONNECTOR_CONTRACT_UNSUPPORTED", "Connector must use Result 1.1");
        }
        return snapshot.getConnector();
    }

    private AiConnector requireCurrentConnector(String connectorId, String tenantId) {
        QueryWrapper<AiConnector> query = new QueryWrapper<>();
        query.lambda().eq(AiConnector::getId, connectorId).eq(AiConnector::getEnabled, true)
                .eq(AiConnector::getDelFlag, 0)
                .and("0".equals(tenantId), q -> q.eq(AiConnector::getTenantId, "0")
                        .or().isNull(AiConnector::getTenantId).or().eq(AiConnector::getTenantId, ""))
                .eq(!"0".equals(tenantId), AiConnector::getTenantId, tenantId);
        AiConnector connector = connectorMapper.selectOne(query);
        if (connector == null) {
            throw AgentExecutionException.nonRetryable("CONNECTOR_CONTRACT_UNSUPPORTED", "Connector is unavailable");
        }
        return connector;
    }

    private URI resolveEndpoint(AgentConfigSnapshot.ConnectorSnapshot snapshot) {
        boolean hasAllowlist = agentProperties.getAllowedHosts() != null
                && agentProperties.getAllowedHosts().stream().anyMatch(StringUtils::hasText);
        if (!hasAllowlist) {
            // HTTP execution is a production-capable path, so an empty allowlist must fail closed.
            throw AgentExecutionException.nonRetryable("CONNECTOR_HOST_NOT_ALLOWED", "Connector host allowlist is empty");
        }
        try {
            return uriPolicy.resolve(snapshot.getBaseUrl(), snapshot.getPath());
        } catch (RuntimeException invalid) {
            throw AgentExecutionException.nonRetryable("CONNECTOR_URL_INVALID", "Connector URL is invalid");
        }
    }

    private byte[] serializeRequest(AgentExecutionRequest request) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("contractVersion", "1.1");
            payload.put("requestId", request.invocationId());
            payload.put("runId", request.runId());
            payload.put("nodeRunId", request.nodeRunId());
            payload.put("attemptNo", request.attemptNo());
            payload.put("resumeGeneration", request.resumeGeneration());
            payload.put("agentCode", request.agentSnapshot().getAgentCode());
            payload.put("systemPrompt", request.agentSnapshot().getSystemPrompt());
            payload.set("input", request.input());
            payload.set("resumeInput", request.resumeInput());
            payload.set("artifactInputs", mapper.valueToTree(request.artifactInputs()));
            payload.put("traceId", request.traceId());
            byte[] bytes = mapper.writeValueAsBytes(payload);
            if (bytes.length > executorProperties.getConnectorMaxRequestBytes()) {
                throw AgentExecutionException.nonRetryable("CONNECTOR_REQUEST_TOO_LARGE", "Agent request is too large");
            }
            return bytes;
        } catch (AgentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw AgentExecutionException.nonRetryable("CONNECTOR_REQUEST_INVALID", "Agent request is invalid");
        }
    }

    private void applyCredential(HttpRequest.Builder builder, AgentConfigSnapshot.ConnectorSnapshot snapshot,
                                 AiConnector current) {
        if (!StringUtils.hasText(current.getSecretCipher()) || "NONE".equals(snapshot.getAuthType())) return;
        String secret = cipher.decrypt(current.getSecretCipher());
        if ("BEARER".equals(snapshot.getAuthType())) builder.header("Authorization", "Bearer " + secret);
        else if ("API_KEY".equals(snapshot.getAuthType())) builder.header(snapshot.getAuthHeader(), secret);
    }

    private byte[] readBounded(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            total += read;
            if (total > limit) {
                throw AgentExecutionException.nonRetryable("CONNECTOR_RESPONSE_TOO_LARGE", "Agent response is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void rejectUnsafeResolution(URI endpoint) {
        try {
            boolean exactAllow = agentProperties.getAllowedHosts() != null
                    && agentProperties.getAllowedHosts().stream().filter(StringUtils::hasText)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(endpoint.getHost().toLowerCase(Locale.ROOT)::equals);
            for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
                boolean unsafe = address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress();
                if (unsafe && !exactAllow) {
                    throw AgentExecutionException.nonRetryable("CONNECTOR_HOST_UNSAFE", "Connector host resolves to a blocked address");
                }
            }
        } catch (AgentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw AgentExecutionException.retryable("CONNECTION_ERROR", "Connector host cannot be resolved");
        }
    }
}
