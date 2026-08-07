package org.jeecg.modules.airag.pipeline;

import org.jeecg.modules.airag.pipeline.contract.AgentResultContract;
import org.jeecg.modules.airag.pipeline.contract.ArtifactDescriptor;
import org.jeecg.modules.airag.pipeline.contract.PipelineEnums;
import org.jeecg.modules.airag.pipeline.validation.AgentResultValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentResultContractTest {
    @Test
    void needsInputRequiresMatchingFlagAndPrompt() {
        AgentResultContract result = new AgentResultContract();
        result.setContractVersion("1.1"); result.setStatus(PipelineEnums.AgentResultStatus.NEEDS_INPUT);
        result.setNeedsUser(true); result.setRetryable(false); result.setUserPrompt("Please clarify");
        assertTrue(new AgentResultValidator().validate(result).isEmpty());
        result.setUserPrompt(null);
        assertFalse(new AgentResultValidator().validate(result).isEmpty());
    }

    @Test
    void artifactsRequireVersionAndRejectNestedSensitiveMetadata() {
        AgentResultContract result = new AgentResultContract();
        result.setContractVersion("1.1"); result.setStatus(PipelineEnums.AgentResultStatus.SUCCESS);
        result.setNeedsUser(false); result.setRetryable(false);
        ArtifactDescriptor artifact = new ArtifactDescriptor();
        artifact.setType(PipelineEnums.ArtifactType.TEXT); artifact.setName("report");
        artifact.setContent(new com.fasterxml.jackson.databind.ObjectMapper().getNodeFactory().textNode("ok"));
        artifact.setMetadata(java.util.Map.of("nested", new com.fasterxml.jackson.databind.ObjectMapper()
                .valueToTree(java.util.Map.of("accessToken", "masked"))));
        result.setArtifacts(java.util.List.of(artifact));
        assertFalse(new AgentResultValidator().validate(result).isEmpty());
    }
}
