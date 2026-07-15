package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;

@Data
public class ConditionNodeConfig implements Serializable {
    private ValueReference left;
    private PipelineEnums.ConditionOperator operator;
    private ValueReference right;

    @Data
    public static class ValueReference implements Serializable {
        private String source;
        private String nodeId;
        private String field;
        private PipelineEnums.ValueType valueType;
        private JsonNode value;
    }
}
