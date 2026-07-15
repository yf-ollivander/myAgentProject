package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.pipeline.validation.PipelineException;
import org.jeecg.modules.airag.pipeline.validation.PipelineErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PipelineDefinitionCodec {
    private final ObjectMapper mapper;

    public PipelineDefinitionCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public PipelineDefinition readDefinition(String json) {
        try {
            return mapper.readValue(json, PipelineDefinition.class);
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

    public ObjectMapper mapper() {
        return mapper;
    }
}
