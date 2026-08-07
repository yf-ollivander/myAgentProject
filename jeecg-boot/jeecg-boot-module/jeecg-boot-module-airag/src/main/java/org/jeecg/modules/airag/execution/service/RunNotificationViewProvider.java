package org.jeecg.modules.airag.execution.service;

import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.BusinessNotificationEvent;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.RunSource;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.RunStatus;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.InterventionView;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.RunSummary;

public interface RunNotificationViewProvider {
    RunCollaborationState state(String runId, AgentAccessContext context);
    NotificationSnapshot build(BusinessNotificationEvent event);

    record RunCollaborationState(RunStatus status, InterventionView openIntervention) {}
    record NotificationSnapshot(String tenantId, String runId, RunSource source, String sourceBotId,
                                String sourceChatId, String sourceThreadId, String notificationBotId,
                                String defaultChatId, RunSummary summary, InterventionView intervention) {}
}
