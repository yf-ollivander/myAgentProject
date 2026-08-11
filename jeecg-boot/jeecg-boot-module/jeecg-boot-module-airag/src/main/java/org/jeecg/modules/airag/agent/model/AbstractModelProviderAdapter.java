package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】复用模型请求发送和严格 Provider JSON 解析-----------
abstract class AbstractModelProviderAdapter implements ModelProviderAdapter {
    protected final ObjectMapper mapper;
    private final ConnectorHttpTransport transport;

    AbstractModelProviderAdapter(ObjectMapper mapper, ConnectorHttpTransport transport) {
        this.mapper = mapper;
        this.transport = transport;
    }

    protected ParsedResponse post(ConnectorRuntimeConfig config, JsonNode body) {
        try {
            ConnectorHttpResponse response = transport.postJson(config, mapper.writeValueAsBytes(body));
            return new ParsedResponse(mapper.readTree(response.body()), response.providerRequestId());
        } catch (ConnectorCallException failure) {
            throw failure;
        } catch (Exception invalid) {
            throw ConnectorCallException.nonRetryable("MODEL_RESPONSE_INVALID", "Model Provider response is invalid");
        }
    }

    protected void addDecimal(JsonNode target, String name, BigDecimal value) {
        if (value != null && target.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) target).put(name, value);
        }
    }

    protected Long longOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.longValue() : null;
    }

    protected JsonNode structured(String text, ModelResponseMode mode) {
        if (mode != ModelResponseMode.RESULT_1_1 || !StringUtils.hasText(text)) {
            return null;
        }
        try {
            return mapper.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    protected record ParsedResponse(JsonNode body, String providerRequestId) {
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】复用模型请求发送和严格 Provider JSON 解析-----------
