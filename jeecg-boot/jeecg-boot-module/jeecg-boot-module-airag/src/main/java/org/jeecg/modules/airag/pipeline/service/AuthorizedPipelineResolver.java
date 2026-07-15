package org.jeecg.modules.airag.pipeline.service;

import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.pipeline.contract.PublishedPipelineSnapshot;
import org.jeecg.modules.airag.pipeline.dto.PipelineDtos;

import java.util.List;

public interface AuthorizedPipelineResolver {
    List<PipelineDtos.PipelineOption> listEnabledOptions(String keyword, int limit, AgentAccessContext context);

    PublishedPipelineSnapshot resolveEnabledByTriggerKey(String botId, String triggerKey,
                                                          AgentAccessContext context);
}
