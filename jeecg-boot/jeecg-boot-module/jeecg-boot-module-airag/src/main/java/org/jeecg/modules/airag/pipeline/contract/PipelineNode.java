package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;

@Data
public class PipelineNode implements Serializable {
    private String id;
    private PipelineEnums.NodeType type;
    private String name;
    private JsonNode config;
}
