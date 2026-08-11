package org.jeecg.modules.airag.agent.model;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】隔离各模型厂商请求和响应协议-----------
public interface ModelProviderAdapter {
    boolean supports(ConnectorProviderType providerType);

    ModelCallResponse invoke(ModelCallRequest request, ConnectorRuntimeConfig runtimeConfig);
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】隔离各模型厂商请求和响应协议-----------
