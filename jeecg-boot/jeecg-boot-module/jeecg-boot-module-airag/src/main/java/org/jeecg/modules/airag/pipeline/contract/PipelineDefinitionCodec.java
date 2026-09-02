package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.pipeline.validation.PipelineException;
import org.jeecg.modules.airag.pipeline.validation.PipelineErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PipelineDefinitionCodec {
    private static final java.util.Set<String> LEGACY_TREE_FIELDS = java.util.Set.of("array", "bigDecimal",
            "bigInteger", "binary", "boolean", "containerNode", "double", "empty", "float",
            "floatingPointNumber", "int", "integralNumber", "long", "missingNode", "nodeType", "null",
            "number", "object", "pojo", "short", "textual", "valueNode");
    private final ObjectMapper mapper;

    public PipelineDefinitionCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public PipelineDefinition readDefinition(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            repairLegacyTreeMetadata(root);
            return mapper.treeToValue(root, PipelineDefinition.class);
        } catch (Exception e) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Pipeline definition JSON is invalid", e.getMessage());
        }
    }

    public PipelineUiModel readUi(String json) {
        try {
            return mapper.readValue(json, PipelineUiModel.class);
        } catch (Exception e) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Pipeline UI JSON is invalid", e.getMessage());
        }
    }

    public <T> T parseNodeConfig(PipelineNode node, Class<T> type) {
        try {
            return mapper.treeToValue(node.getConfig(), type);
        } catch (Exception e) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Node configuration is invalid", node.getId());
        }
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Pipeline JSON cannot be serialized", e.getMessage());
        }
    }

    public JsonNode valueToTree(Object value) {
        return mapper.valueToTree(value);
    }

    public Object readUntyped(String json) {
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Pipeline JSON is invalid", e.getMessage());
        }
    }

    // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】只修复已确认的 Jackson 3 误序列化特征，其他未知字段仍由严格解码拒绝-----------
    private void repairLegacyTreeMetadata(JsonNode root) {
        JsonNode nodes = root == null ? null : root.get("nodes");
        if (nodes == null || !nodes.isArray()) return;
        nodes.forEach(node -> {
            JsonNode config = node.get("config");
            if (config instanceof ObjectNode object
                    && object.path("containerNode").asBoolean(false)
                    && "OBJECT".equals(object.path("nodeType").asText())
                    && object.path("object").asBoolean(false)
                    && !object.path("valueNode").asBoolean(true)) {
                LEGACY_TREE_FIELDS.forEach(object::remove);
            }
        });
    }
    // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】只修复已确认的 Jackson 3 误序列化特征，其他未知字段仍由严格解码拒绝-----------

    public ObjectMapper mapper() {
        return mapper;
    }
}
