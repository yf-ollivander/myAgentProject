package org.jeecg.modules.airag.execution.gateway;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.contract.AgentResultContract;
public class DisabledAgentExecutionGateway implements AgentExecutionGateway {
    @Override public AgentResultContract execute(AgentExecutionRequest request) {
        throw ExecutionException.of(ExecutionErrorCode.EXECUTOR_GATEWAY_UNAVAILABLE, "No executable Agent gateway is installed");
    }
}
