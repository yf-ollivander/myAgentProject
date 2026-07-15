package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ArtifactDescriptor implements Serializable {
    private PipelineEnums.ArtifactType type;
    private String name;
    private String uri;
    private JsonNode content;
    private String checksum;
    private String version;
    private Map<String, JsonNode> metadata = new LinkedHashMap<>();
}
