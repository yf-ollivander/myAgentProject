package org.jeecg.modules.airag.execution.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.pipeline.contract.AgentResultContract;
import org.jeecg.modules.airag.pipeline.contract.ArtifactDescriptor;

import java.util.List;

public interface AgentExecutionGateway {
    AgentResultContract execute(AgentExecutionRequest request);

    record AgentExecutionRequest(String invocationId, String runId, String nodeRunId,
                                 int attemptNo, int resumeGeneration, String traceId,
                                 AgentConfigSnapshot agentSnapshot, JsonNode input,
                                 JsonNode resumeInput, List<ArtifactDescriptor> artifactInputs) {}
}
