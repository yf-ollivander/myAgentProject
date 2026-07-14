package org.jeecg.modules.airag.agent.service;

import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;

public interface AgentConfigProvider {
    AgentConfigSnapshot getEnabledSnapshot(String agentId);
}
