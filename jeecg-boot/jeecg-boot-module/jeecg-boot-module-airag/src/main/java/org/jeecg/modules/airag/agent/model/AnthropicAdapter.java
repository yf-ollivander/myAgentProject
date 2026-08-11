package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】实现 Anthropic system/messages/content 协议-----------
@Component
public class AnthropicAdapter extends AbstractModelProviderAdapter {
    public AnthropicAdapter(ObjectMapper mapper, ConnectorHttpTransport transport) {
        super(mapper, transport);
    }

    @Override
    public boolean supports(ConnectorProviderType providerType) {
        return providerType == ConnectorProviderType.ANTHROPIC;
    }

    @Override
    public ModelCallResponse invoke(ModelCallRequest request, ConnectorRuntimeConfig config) {
        ObjectNode body = mapper.createObjectNode().put("model", request.getModelName())
                .put("max_tokens", request.getModelOptions().getMaxTokens() == null ? 4096 : request.getModelOptions().getMaxTokens());
        if (StringUtils.hasText(request.getSystemPrompt())) body.put("system", request.getSystemPrompt());
        body.putArray("messages").addObject().put("role", "user").put("content", request.getUserPrompt());
        addDecimal(body, "temperature", request.getModelOptions().getTemperature());
        addDecimal(body, "top_p", request.getModelOptions().getTopP());
        ParsedResponse response = post(config, body);
        StringBuilder text = new StringBuilder();
        response.body().path("content").forEach(part -> {
            if ("text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
                text.append(part.path("text").textValue());
            }
        });
        String content = text.isEmpty() ? null : text.toString();
        JsonNode usage = response.body().path("usage");
        Long input = longOrNull(usage.get("input_tokens"));
        Long output = longOrNull(usage.get("output_tokens"));
        return ModelCallResponse.builder().text(content).structuredOutput(structured(content, request.getResponseMode()))
                .finishReason(response.body().path("stop_reason").asText(null))
                .usage(new ModelUsage(input, output, input == null || output == null ? null : input + output))
                .providerRequestId(response.providerRequestId()).build();
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】实现 Anthropic system/messages/content 协议-----------
