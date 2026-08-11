package org.jeecg.modules.airag.agent.model;

import java.net.http.HttpHeaders;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】向 Adapter 暴露有界响应和 Provider 请求标识-----------
public record ConnectorHttpResponse(int statusCode, byte[] body, HttpHeaders headers) {
    public String providerRequestId() {
        return headers.firstValue("x-request-id")
                .or(() -> headers.firstValue("request-id"))
                .or(() -> headers.firstValue("cf-ray")).orElse(null);
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】向 Adapter 暴露有界响应和 Provider 请求标识-----------
