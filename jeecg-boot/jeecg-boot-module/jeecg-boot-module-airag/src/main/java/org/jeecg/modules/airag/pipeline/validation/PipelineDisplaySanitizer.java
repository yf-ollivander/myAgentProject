package org.jeecg.modules.airag.pipeline.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class PipelineDisplaySanitizer {
    private static final Set<String> BLOCKED = Set.of("systemprompt", "baseurl", "path", "requestheaders",
            "authorization", "secret", "appsecret", "encryptkey", "verificationtoken", "secretcipher");
    private final PipelineDefinitionCodec codec;

    public PipelineDisplaySanitizer(PipelineDefinitionCodec codec) {
        this.codec = codec;
    }

    public JsonNode sanitize(String definitionJson) {
        try {
            JsonNode root = codec.mapper().readTree(definitionJson).deepCopy();
            sanitizeNode(root);
            return root;
        } catch (Exception e) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Published definition cannot be sanitized", e.getMessage());
        }
    }

    private void sanitizeNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            if (object.has("agentSnapshot")) {
                JsonNode snapshot = object.remove("agentSnapshot");
                ObjectNode ref = object.objectNode();
                ref.set("agentId", snapshot.get("agentId"));
                ref.set("agentCode", snapshot.get("agentCode"));
                ref.set("name", snapshot.get("name"));
                object.set("agentRef", ref);
            }
            Iterator<String> names = object.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (BLOCKED.contains(name.toLowerCase(Locale.ROOT))) names.remove();
            }
            object.elements().forEachRemaining(this::sanitizeNode);
        } else if (node instanceof ArrayNode array) {
            array.elements().forEachRemaining(this::sanitizeNode);
        }
    }
}
