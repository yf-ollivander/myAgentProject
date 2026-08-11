package org.jeecg.modules.airag.execution.gateway;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.mapper.AiConnectorMapper;
import org.jeecg.modules.airag.agent.model.ConnectorCallException;
import org.jeecg.modules.airag.agent.model.ConnectorContractPolicy;
import org.jeecg.modules.airag.agent.model.ConnectorInvocationService;
import org.jeecg.modules.airag.pipeline.contract.AgentResultContract;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】让正式 Gateway 复用统一 Custom/模型调用服务-----------
@Component
@ConditionalOnProperty(name = "ai.executor.gateway", havingValue = "http")
public class HttpAgentExecutionGateway implements AgentExecutionGateway {
    private final AiConnectorMapper connectorMapper;
    private final ConnectorInvocationService invocationService;

    public HttpAgentExecutionGateway(AiConnectorMapper connectorMapper, ConnectorInvocationService invocationService) {
        this.connectorMapper = connectorMapper;
        this.invocationService = invocationService;
    }

    @Override
    public AgentResultContract execute(AgentExecutionRequest request) {
        AgentConfigSnapshot.ConnectorSnapshot snapshot = requireSnapshot(request);
        AiConnector current = requireCurrentConnector(snapshot.getConnectorId(), request.tenantId());
        try {
            return invocationService.execute(request, current);
        } catch (ConnectorCallException failure) {
            throw new AgentExecutionException(failure.getErrorCode(), failure.getMessage(), failure.isRetryable());
        }
    }

    private AgentConfigSnapshot.ConnectorSnapshot requireSnapshot(AgentExecutionRequest request) {
        AgentConfigSnapshot snapshot = request.agentSnapshot();
        if (snapshot == null || !ConnectorContractPolicy.isPipelineCompatible(snapshot.getConnector())) {
            throw AgentExecutionException.nonRetryable(
                    "CONNECTOR_CONTRACT_UNSUPPORTED", "Connector cannot produce Result 1.1");
        }
        return snapshot.getConnector();
    }

    private AiConnector requireCurrentConnector(String connectorId, String tenantId) {
        QueryWrapper<AiConnector> query = new QueryWrapper<>();
        query.lambda().eq(AiConnector::getId, connectorId).eq(AiConnector::getEnabled, true)
                .eq(AiConnector::getDelFlag, 0)
                .and("0".equals(tenantId), q -> q.eq(AiConnector::getTenantId, "0")
                        .or().isNull(AiConnector::getTenantId).or().eq(AiConnector::getTenantId, ""))
                .eq(!"0".equals(tenantId), AiConnector::getTenantId, tenantId);
        AiConnector connector = connectorMapper.selectOne(query);
        if (connector == null) {
            throw AgentExecutionException.nonRetryable("CONNECTOR_CONTRACT_UNSUPPORTED", "Connector is unavailable");
        }
        return connector;
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】让正式 Gateway 复用统一 Custom/模型调用服务-----------
