package org.jeecg.modules.airag.execution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PipelineTemplateResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(run\\.input(?:\\.[A-Za-z0-9_-]+)*|nodes\\.[A-Za-z0-9_-]+\\.(?:output(?:\\.[A-Za-z0-9_-]+)*|summary))}}" );

    public JsonNode resolve(JsonNode template, JsonNode runInput, Map<String, JsonNode> outputs, Map<String, String> summaries) {
        if (template == null) return NullNode.instance;
        if (template.isTextual()) return resolveText(template.textValue(), runInput, outputs, summaries);
        if (template.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = template.fields();
            fields.forEachRemaining(entry -> result.set(entry.getKey(), resolve(entry.getValue(), runInput, outputs, summaries)));
            return result;
        }
        if (template.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            template.forEach(item -> result.add(resolve(item, runInput, outputs, summaries)));
            return result;
        }
        return template.deepCopy();
    }

    private JsonNode resolveText(String value, JsonNode input, Map<String, JsonNode> outputs, Map<String, String> summaries) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.find()) {
            if (value.contains("{{")) throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST, "Unsupported template expression");
            return TextNode.valueOf(value);
        }
        matcher.reset();
        if (matcher.matches()) return lookup(matcher.group(1), input, outputs, summaries);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(rendered, Matcher.quoteReplacement(asText(lookup(matcher.group(1), input, outputs, summaries))));
        matcher.appendTail(rendered);
        return TextNode.valueOf(rendered.toString());
    }

    private JsonNode lookup(String expression, JsonNode input, Map<String, JsonNode> outputs, Map<String, String> summaries) {
        String[] parts = expression.split("\\.");
        JsonNode value;
        int offset;
        if (expression.startsWith("run.input")) { value = input; offset = 2; }
        else {
            String nodeId = parts[1];
            if ("summary".equals(parts[2])) return TextNode.valueOf(summaries.getOrDefault(nodeId, ""));
            value = outputs.get(nodeId); offset = 3;
        }
        for (int i = offset; i < parts.length && value != null; i++) value = value.get(parts[i]);
        if (value == null || value.isMissingNode()) throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST,
                "Required template value is missing: " + expression);
        return value.deepCopy();
    }

    private String asText(JsonNode value) { return value.isTextual() ? value.textValue() : value.toString(); }
}
