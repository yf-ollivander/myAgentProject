package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】实现 Gemini generateContent 协议-----------
@Component
public class GeminiAdapter extends AbstractModelProviderAdapter {
    public GeminiAdapter(ObjectMapper mapper, ConnectorHttpTransport transport) {
        super(mapper, transport);
    }

    @Override
    public boolean supports(ConnectorProviderType providerType) {
        return providerType == ConnectorProviderType.GEMINI;
    }

    @Override
    public ModelCallResponse invoke(ModelCallRequest request, ConnectorRuntimeConfig config) {
        ObjectNode body = mapper.createObjectNode();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            body.putObject("systemInstruction").putArray("parts").addObject().put("text", request.getSystemPrompt());
        }
        body.putArray("contents").addObject().put("role", "user").putArray("parts")
                .addObject().put("text", request.getUserPrompt());
        ObjectNode generation = body.putObject("generationConfig");
        addDecimal(generation, "temperature", request.getModelOptions().getTemperature());
        addDecimal(generation, "topP", request.getModelOptions().getTopP());
        if (request.getModelOptions().getMaxTokens() != null) generation.put("maxOutputTokens", request.getModelOptions().getMaxTokens());
        if (request.getResponseMode() == ModelResponseMode.RESULT_1_1) generation.put("responseMimeType", "application/json");
        ParsedResponse response = post(config, body);
        JsonNode candidate = response.body().path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        candidate.path("content").path("parts").forEach(part -> {
            if (part.path("text").isTextual()) text.append(part.path("text").textValue());
        });
        String content = text.isEmpty() ? null : text.toString();
        JsonNode usage = response.body().path("usageMetadata");
        return ModelCallResponse.builder().text(content).structuredOutput(structured(content, request.getResponseMode()))
                .finishReason(candidate.path("finishReason").asText(null))
                .usage(new ModelUsage(longOrNull(usage.get("promptTokenCount")), longOrNull(usage.get("candidatesTokenCount")),
                        longOrNull(usage.get("totalTokenCount"))))
                .providerRequestId(response.providerRequestId()).build();
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】实现 Gemini generateContent 协议-----------
