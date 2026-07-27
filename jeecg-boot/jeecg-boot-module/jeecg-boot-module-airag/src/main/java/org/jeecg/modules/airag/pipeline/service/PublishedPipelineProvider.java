package org.jeecg.modules.airag.pipeline.service;

import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.pipeline.contract.PublishedPipelineSnapshot;

public interface PublishedPipelineProvider {
    PublishedPipelineSnapshot getAuthorizedVersionById(String versionId, AgentAccessContext context);

    PublishedPipelineSnapshot resolveEnabledForRun(String pipelineId, AgentAccessContext context);
}
