package org.jeecg.modules.airag.execution.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.ArrayList;

@Component
@ConditionalOnProperty(name="ai.executor.gateway", havingValue="mock")
public class MockAgentExecutionGateway implements AgentExecutionGateway {
    @Override
    public AgentResultContract execute(AgentExecutionRequest request) {
        JsonNode input = request.input();
        PipelineEnums.AgentResultStatus status = input != null && input.has("__mockStatus")
                ? PipelineEnums.AgentResultStatus.valueOf(input.get("__mockStatus").asText())
                : PipelineEnums.AgentResultStatus.SUCCESS;
        AgentResultContract result = new AgentResultContract();
        result.setContractVersion("1.1"); result.setStatus(status);
        result.setNeedsUser(status == PipelineEnums.AgentResultStatus.NEEDS_INPUT);
        result.setRetryable(status == PipelineEnums.AgentResultStatus.FAILED && input.path("__mockRetryable").asBoolean(false));
        result.setSummary(input != null && input.has("__mockSummary") ? input.get("__mockSummary").asText() : "Mock execution completed");
        result.setOutput(input != null && input.has("__mockOutput") ? input.get("__mockOutput") : input);
        result.setUserPrompt(status == PipelineEnums.AgentResultStatus.NEEDS_INPUT
                ? input.path("__mockUserPrompt").asText("Additional input is required") : null);
        if (status == PipelineEnums.AgentResultStatus.FAILED) {
            result.setErrorCode(input.path("__mockErrorCode").asText("MOCK_FAILED"));
            result.setErrorMessage(input.path("__mockErrorMessage").asText("Mock execution failed"));
        }
        result.setArtifacts(input != null && input.has("__mockArtifacts")
                ? new com.fasterxml.jackson.databind.ObjectMapper().convertValue(input.get("__mockArtifacts"),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {}) : new ArrayList<>());
        return result;
    }
}
