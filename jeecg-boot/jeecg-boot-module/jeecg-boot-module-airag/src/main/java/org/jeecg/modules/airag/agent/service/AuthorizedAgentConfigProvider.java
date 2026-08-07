package org.jeecg.modules.airag.agent.service;

import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;

import java.util.List;

public interface AuthorizedAgentConfigProvider {
    List<AiConfigDtos.AgentOption> listEnabledOptions(String keyword, int limit, AgentAccessContext accessContext);

    AgentConfigSnapshot resolveEnabledSnapshot(String agentId, AgentAccessContext accessContext);

    AgentConfigSnapshot resolveEnabledSnapshotByCode(String agentCode, AgentAccessContext accessContext);

    AgentConfigSnapshot resolveEnabledSnapshotByBotId(String botId, AgentAccessContext accessContext);
}
