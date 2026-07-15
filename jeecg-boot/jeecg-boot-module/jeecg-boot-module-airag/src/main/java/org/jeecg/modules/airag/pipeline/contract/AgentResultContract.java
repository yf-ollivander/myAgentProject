package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AgentResultContract implements Serializable {
    private String contractVersion;
    private PipelineEnums.AgentResultStatus status;
    private String summary;
    private JsonNode output;
    private List<ArtifactDescriptor> artifacts = new ArrayList<>();
    private Boolean needsUser;
    private String userPrompt;
    private Boolean retryable;
    private String errorCode;
    private String errorMessage;
}
