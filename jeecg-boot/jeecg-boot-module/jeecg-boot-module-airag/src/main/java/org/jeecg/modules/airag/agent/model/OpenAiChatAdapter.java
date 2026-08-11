package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】实现 OpenAI-compatible 与 DeepSeek 共用协议-----------
@Component
public class OpenAiChatAdapter extends AbstractModelProviderAdapter {
    public OpenAiChatAdapter(ObjectMapper mapper, ConnectorHttpTransport transport) {
        super(mapper, transport);
    }

    @Override
    public boolean supports(ConnectorProviderType providerType) {
        return providerType == ConnectorProviderType.OPENAI_COMPATIBLE || providerType == ConnectorProviderType.DEEPSEEK;
    }

    @Override
    public ModelCallResponse invoke(ModelCallRequest request, ConnectorRuntimeConfig config) {
        ObjectNode body = mapper.createObjectNode().put("model", request.getModelName());
        ArrayNode messages = body.putArray("messages");
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
        }
        messages.addObject().put("role", "user").put("content", request.getUserPrompt());
        addDecimal(body, "temperature", request.getModelOptions().getTemperature());
        addDecimal(body, "top_p", request.getModelOptions().getTopP());
        if (request.getModelOptions().getMaxTokens() != null) body.put("max_tokens", request.getModelOptions().getMaxTokens());
        if (request.getResponseMode() == ModelResponseMode.RESULT_1_1) {
            body.putObject("response_format").put("type", "json_object");
        }
        ParsedResponse response = post(config, body);
        JsonNode choice = response.body().path("choices").path(0);
        String text = choice.path("message").path("content").isTextual()
                ? choice.path("message").path("content").textValue() : null;
        JsonNode usage = response.body().path("usage");
        return ModelCallResponse.builder().text(text).structuredOutput(structured(text, request.getResponseMode()))
                .finishReason(choice.path("finish_reason").asText(null))
                .usage(new ModelUsage(longOrNull(usage.get("prompt_tokens")), longOrNull(usage.get("completion_tokens")),
                        longOrNull(usage.get("total_tokens"))))
                .providerRequestId(response.providerRequestId()).build();
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】实现 OpenAI-compatible 与 DeepSeek 共用协议-----------
