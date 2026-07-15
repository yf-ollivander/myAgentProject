package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class EndNodeConfig implements Serializable {
    private Map<String, JsonNode> output;
    private List<ArtifactInput> artifactSelection = new ArrayList<>();
    private String completionSummary;
}
