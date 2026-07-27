package org.jeecg.modules.airag.execution.config;

import org.jeecg.modules.airag.execution.gateway.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class AgentGatewayConfiguration {
    @Bean @ConditionalOnMissingBean(AgentExecutionGateway.class)
    public AgentExecutionGateway disabledAgentExecutionGateway(){return new DisabledAgentExecutionGateway();}
}
