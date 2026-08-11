package org.jeecg.modules.airag.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.model.ConnectorInvocationService;
import org.jeecg.modules.airag.agent.service.AgentConnectorInvoker;
import org.springframework.stereotype.Component;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】让配置测试复用正式执行的统一 Connector 调用层-----------
@Component
public class HttpAgentConnectorInvoker implements AgentConnectorInvoker {
    private final ConnectorInvocationService invocationService;

    public HttpAgentConnectorInvoker(ConnectorInvocationService invocationService) {
        this.invocationService = invocationService;
    }

    @Override
    public AiConfigDtos.AgentExecutionResult execute(AiAgent agent, AiConnector connector, JsonNode input) {
        return invocationService.execute(agent, connector, input);
    }

    @Override
    public AiConfigDtos.ConnectionTestResult test(AiConnector connector, JsonNode input) {
        return invocationService.test(connector, input);
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】让配置测试复用正式执行的统一 Connector 调用层-----------
