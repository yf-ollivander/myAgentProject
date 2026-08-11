package org.jeecg.modules.airag.agent.model;

import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.springframework.util.StringUtils;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】统一发布、运行和 Gateway 的 Connector 准入规则-----------
public final class ConnectorContractPolicy {
    private ConnectorContractPolicy() {
    }

    public static boolean isPipelineCompatible(AgentConfigSnapshot.ConnectorSnapshot connector) {
        if (connector == null) {
            return false;
        }
        try {
            ConnectorProviderType provider = ConnectorProviderType.fromNullable(connector.getProviderType());
            if (!provider.isModelProvider()) {
                return AiConfigDtos.CONTRACT_1_1.equals(connector.getResultContractVersion());
            }
            return StringUtils.hasText(connector.getModelName())
                    && (ModelResponseMode.fromNullable(connector.getModelResponseMode()) == ModelResponseMode.TEXT
                    || ModelResponseMode.fromNullable(connector.getModelResponseMode()) == ModelResponseMode.RESULT_1_1);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static String effectiveResultContract(ConnectorProviderType provider, String requested) {
        return provider.isModelProvider() ? AiConfigDtos.CONTRACT_1_1 : requested;
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】统一发布、运行和 Gateway 的 Connector 准入规则-----------
