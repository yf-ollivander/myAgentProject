package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】统一测试与正式执行所需的无密钥运行配置-----------
@Value
@Builder(toBuilder = true)
public class ConnectorRuntimeConfig {
    String connectorId;
    ConnectorProviderType providerType;
    String modelName;
    AiConfigDtos.ModelOptions modelOptions;
    ModelResponseMode responseMode;
    String baseUrl;
    String path;
    String authType;
    String authHeader;
    Map<String, String> requestHeaders;
    String secretCipher;
    int connectTimeout;
    int readTimeout;
    AiConfigDtos.ResponseMapping responseMapping;
    String resultContractVersion;

    public static ConnectorRuntimeConfig fromEntity(AiConnector connector, ObjectMapper mapper) {
        return builderFrom(connector, mapper)
                .baseUrl(connector.getBaseUrl()).path(connector.getPath())
                .authType(connector.getAuthType()).authHeader(connector.getAuthHeader())
                .connectTimeout(connector.getConnectTimeout()).readTimeout(connector.getReadTimeout()).build();
    }

    public static ConnectorRuntimeConfig fromSnapshot(AgentConfigSnapshot.ConnectorSnapshot snapshot,
                                                       AiConnector current, ObjectMapper mapper) {
        try {
            ConnectorProviderType provider = ConnectorProviderType.fromNullable(snapshot.getProviderType());
            return ConnectorRuntimeConfig.builder()
                    .connectorId(snapshot.getConnectorId()).providerType(provider)
                    .modelName(snapshot.getModelName()).modelOptions(defaultOptions(snapshot.getModelOptions()))
                    .responseMode(ModelResponseMode.fromNullable(snapshot.getModelResponseMode()))
                    .baseUrl(snapshot.getBaseUrl()).path(snapshot.getPath()).authType(snapshot.getAuthType())
                    .authHeader(snapshot.getAuthHeader()).requestHeaders(snapshot.getRequestHeaders() == null
                            ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshot.getRequestHeaders()))
                    .secretCipher(current.getSecretCipher()).connectTimeout(snapshot.getConnectTimeout() == null ? 10 : snapshot.getConnectTimeout())
                    .readTimeout(snapshot.getReadTimeout() == null ? 300 : snapshot.getReadTimeout()).responseMapping(snapshot.getResponseMapping())
                    .resultContractVersion(snapshot.getResultContractVersion()).build();
        } catch (Exception invalid) {
            throw ConnectorCallException.nonRetryable("CONNECTOR_CONFIG_INVALID", "Connector snapshot configuration is invalid");
        }
    }

    private static ConnectorRuntimeConfigBuilder builderFrom(AiConnector connector, ObjectMapper mapper) {
        try {
            ConnectorProviderType provider = ConnectorProviderType.fromNullable(connector.getProviderType());
            Map<String, String> headers = StringUtils.hasText(connector.getRequestHeaders())
                    ? mapper.readValue(connector.getRequestHeaders(), new TypeReference<>() {}) : new LinkedHashMap<>();
            AiConfigDtos.ModelOptions options = provider.isModelProvider() && StringUtils.hasText(connector.getModelOptions())
                    ? mapper.readValue(connector.getModelOptions(), AiConfigDtos.ModelOptions.class) : new AiConfigDtos.ModelOptions();
            AiConfigDtos.ResponseMapping mapping = provider == ConnectorProviderType.CUSTOM
                    && AiConfigDtos.CONTRACT_LEGACY.equals(connector.getResultContractVersion())
                    && StringUtils.hasText(connector.getResponseMapping())
                    ? mapper.readValue(connector.getResponseMapping(), AiConfigDtos.ResponseMapping.class) : null;
            return ConnectorRuntimeConfig.builder().connectorId(connector.getId()).providerType(provider)
                    .modelName(connector.getModelName()).modelOptions(defaultOptions(options))
                    .responseMode(ModelResponseMode.fromNullable(connector.getModelResponseMode()))
                    .requestHeaders(headers).secretCipher(connector.getSecretCipher())
                    .responseMapping(mapping).resultContractVersion(StringUtils.hasText(connector.getResultContractVersion())
                            ? connector.getResultContractVersion() : AiConfigDtos.CONTRACT_LEGACY);
        } catch (Exception invalid) {
            throw ConnectorCallException.nonRetryable("CONNECTOR_CONFIG_INVALID", "Connector configuration is invalid");
        }
    }

    private static AiConfigDtos.ModelOptions defaultOptions(AiConfigDtos.ModelOptions options) {
        return options == null ? new AiConfigDtos.ModelOptions() : options;
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】统一测试与正式执行所需的无密钥运行配置-----------
