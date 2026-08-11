package org.jeecg.modules.airag.agent.support;

import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AgentConfigSnapshotSanitizer {
    private static final Set<String> PROTECTED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "x-api-key", "cookie", "set-cookie");
    private static final Set<String> SENSITIVE_TERMS = Set.of(
            "secret", "token", "password", "credential", "key");

    public AgentConfigSnapshot sanitize(AgentConfigSnapshot source) {
        if (source == null) {
            throw new IllegalArgumentException("Agent snapshot is required");
        }
        AgentConfigSnapshot.ConnectorSnapshot connector = sanitizeConnector(source.getConnector());
        return AgentConfigSnapshot.builder()
                .agentId(source.getAgentId()).agentCode(source.getAgentCode()).name(source.getName())
                .systemPrompt(source.getSystemPrompt()).timeoutSeconds(source.getTimeoutSeconds())
                .maxRetry(source.getMaxRetry()).connector(connector).feishuBot(source.getFeishuBot()).build();
    }

    private AgentConfigSnapshot.ConnectorSnapshot sanitizeConnector(AgentConfigSnapshot.ConnectorSnapshot source) {
        if (source == null) {
            return null;
        }
        Map<String, String> safeHeaders = new LinkedHashMap<>();
        if (source.getRequestHeaders() != null) {
            // Snapshots are persisted; execution-time credentials must be resolved by ID instead of frozen here.
            source.getRequestHeaders().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(
                            name -> name.toLowerCase(Locale.ROOT))))
                    .filter(entry -> isSafeHeader(entry.getKey(), source.getAuthHeader()))
                    .forEach(entry -> safeHeaders.put(entry.getKey(), entry.getValue()));
        }
        return AgentConfigSnapshot.ConnectorSnapshot.builder()
                .connectorId(source.getConnectorId()).connectorCode(source.getConnectorCode())
                .baseUrl(source.getBaseUrl()).path(source.getPath()).authType(source.getAuthType())
                .authHeader(source.getAuthHeader()).requestHeaders(safeHeaders)
                // Result 1.1 is schema-driven; strip legacy pointers even from manually constructed snapshots.
                .responseMapping(AiConfigDtos.CONTRACT_1_1.equals(source.getResultContractVersion())
                        ? null : source.getResponseMapping())
                .resultContractVersion(source.getResultContractVersion())
                // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】保留非敏感模型配置供运行时适配-----------
                .providerType(source.getProviderType()).modelName(source.getModelName())
                .modelOptions(source.getModelOptions()).modelResponseMode(source.getModelResponseMode())
                // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】保留非敏感模型配置供运行时适配-----------
                .connectTimeout(source.getConnectTimeout())
                .readTimeout(source.getReadTimeout()).secretConfigured(source.isSecretConfigured()).build();
    }

    private boolean isSafeHeader(String name, String authHeader) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (PROTECTED_HEADERS.contains(normalized)
                || (StringUtils.hasText(authHeader) && normalized.equals(authHeader.toLowerCase(Locale.ROOT)))) {
            return false;
        }
        return SENSITIVE_TERMS.stream().noneMatch(normalized::contains);
    }
}
