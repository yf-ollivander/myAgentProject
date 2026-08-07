package org.jeecg.modules.airag.execution.service;

import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.InterventionResolveRequest;

public interface RunInterventionService {
    void resolve(String runId, String interventionId, InterventionResolveRequest request,
                 AgentAccessContext context);
}
