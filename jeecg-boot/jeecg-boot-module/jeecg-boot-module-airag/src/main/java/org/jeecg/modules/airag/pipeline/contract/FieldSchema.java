package org.jeecg.modules.airag.pipeline.contract;

import lombok.Data;

import java.io.Serializable;

@Data
public class FieldSchema implements Serializable {
    private String name;
    private String field;
    private PipelineEnums.ValueType type;
    private Boolean required;

    public String effectiveName() {
        return name == null ? field : name;
    }
}
