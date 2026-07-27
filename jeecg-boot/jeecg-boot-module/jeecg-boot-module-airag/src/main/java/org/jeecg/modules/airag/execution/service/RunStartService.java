package org.jeecg.modules.airag.execution.service;

import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.*;

public interface RunStartService {
    RunCreateResult startPipeline(RunCreateRequest request, AgentAccessContext context, RunSourceContext source);
    RunCreateResult startDirectAgent(RunCreateRequest request, AgentAccessContext context, RunSourceContext source);
}
