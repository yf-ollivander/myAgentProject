package org.jeecg.modules.airag.agent.model;

import org.springframework.stereotype.Service;

import java.util.List;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】通过注册表选择唯一 Provider Adapter-----------
@Service
public class ModelCallService {
    private final List<ModelProviderAdapter> adapters;

    public ModelCallService(List<ModelProviderAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public ModelCallResponse invoke(ModelCallRequest request, ConnectorRuntimeConfig config) {
        return adapters.stream().filter(adapter -> adapter.supports(config.getProviderType())).findFirst()
                .orElseThrow(() -> ConnectorCallException.nonRetryable(
                        "CONNECTOR_PROVIDER_UNSUPPORTED", "Model Provider is unsupported"))
                .invoke(request, config);
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】通过注册表选择唯一 Provider Adapter-----------
