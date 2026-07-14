package org.jeecg.modules.airag.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiConnector;

public interface AgentConnectorInvoker {
    AiConfigDtos.AgentExecutionResult execute(AiAgent agent, AiConnector connector, JsonNode input);

    AiConfigDtos.ConnectionTestResult test(AiConnector connector, JsonNode input);
}
