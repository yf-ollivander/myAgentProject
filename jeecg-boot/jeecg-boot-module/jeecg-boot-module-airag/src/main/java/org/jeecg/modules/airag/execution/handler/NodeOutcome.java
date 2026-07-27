package org.jeecg.modules.airag.execution.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.modules.airag.pipeline.contract.ArtifactDescriptor;
import org.jeecg.modules.airag.pipeline.contract.PipelineEnums;
import java.util.List;

public record NodeOutcome(PipelineEnums.AgentResultStatus status, JsonNode output, String summary,
                          List<ArtifactDescriptor> artifacts, String selectedBranch,
                          boolean retryable, String errorCode, String errorMessage,
                          String userPrompt, String notificationMessage) {
    public static NodeOutcome success(JsonNode output,String summary,String branch){
        return new NodeOutcome(PipelineEnums.AgentResultStatus.SUCCESS,output,summary,List.of(),branch,false,null,null,null,null);
    }
}
