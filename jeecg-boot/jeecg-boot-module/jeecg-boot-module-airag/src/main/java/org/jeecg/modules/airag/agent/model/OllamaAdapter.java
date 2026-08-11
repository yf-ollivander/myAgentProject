package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】实现 Ollama 非流式 chat 协议-----------
@Component
public class OllamaAdapter extends AbstractModelProviderAdapter {
    public OllamaAdapter(ObjectMapper mapper, ConnectorHttpTransport transport) {
        super(mapper, transport);
    }

    @Override
    public boolean supports(ConnectorProviderType providerType) {
        return providerType == ConnectorProviderType.OLLAMA;
    }

    @Override
    public ModelCallResponse invoke(ModelCallRequest request, ConnectorRuntimeConfig config) {
        ObjectNode body = mapper.createObjectNode().put("model", request.getModelName()).put("stream", false);
        var messages = body.putArray("messages");
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
        }
        messages.addObject().put("role", "user").put("content", request.getUserPrompt());
        ObjectNode options = body.putObject("options");
        addDecimal(options, "temperature", request.getModelOptions().getTemperature());
        addDecimal(options, "top_p", request.getModelOptions().getTopP());
        if (request.getModelOptions().getMaxTokens() != null) options.put("num_predict", request.getModelOptions().getMaxTokens());
        if (request.getResponseMode() == ModelResponseMode.RESULT_1_1) body.put("format", "json");
        ParsedResponse response = post(config, body);
        String text = response.body().path("message").path("content").isTextual()
                ? response.body().path("message").path("content").textValue() : null;
        Long input = longOrNull(response.body().get("prompt_eval_count"));
        Long output = longOrNull(response.body().get("eval_count"));
        return ModelCallResponse.builder().text(text).structuredOutput(structured(text, request.getResponseMode()))
                .finishReason(response.body().path("done_reason").asText(null))
                .usage(new ModelUsage(input, output, input == null || output == null ? null : input + output))
                .providerRequestId(response.providerRequestId()).build();
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】实现 Ollama 非流式 chat 协议-----------
