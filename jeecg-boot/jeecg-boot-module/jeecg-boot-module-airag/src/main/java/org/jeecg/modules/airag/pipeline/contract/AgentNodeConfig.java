package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AgentNodeConfig implements Serializable {
    private String stageCode;
    private String agentId;
    private String resultContractVersion;
    private Map<String, JsonNode> input;
    private List<FieldSchema> outputSchema = new ArrayList<>();
    private List<PipelineEnums.ArtifactType> artifactOutputs = new ArrayList<>();
    private List<ArtifactInput> artifactInputs = new ArrayList<>();
    private PipelineEnums.ErrorPolicy onError;
    private AgentConfigSnapshot agentSnapshot;
}
