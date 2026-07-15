package org.jeecg.modules.airag.pipeline.service;

import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.pipeline.dto.PipelineDtos;

public interface PipelinePublishService {
    PipelineDtos.PublishResult publish(String pipelineId, PipelineDtos.PublishRequest request,
                                       AgentAccessContext context);
}
