package org.jeecg.modules.airag.execution;

import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.mapper.*;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.DependencyType;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DependencyCancellationServiceTest {
    @Test void disabledAgentMakesRunUnavailable(){AiRunDependencyMapper dependencies=mock(AiRunDependencyMapper.class);AiAgentMapper agents=mock(AiAgentMapper.class);AiRunDependency dependency=new AiRunDependency();dependency.setDependencyType(DependencyType.AGENT.name());dependency.setDependencyId("a");when(dependencies.selectList(any())).thenReturn(List.of(dependency));AiAgent agent=new AiAgent();agent.setTenantId("0");agent.setEnabled(false);agent.setDelFlag(0);when(agents.selectById("a")).thenReturn(agent);
        RunDependencyAvailabilityService service=new RunDependencyAvailabilityService(dependencies,mock(AiPipelineMapper.class),agents,mock(AiConnectorMapper.class),mock(AiFeishuBotMapper.class),mock(AiRunMapper.class),mock(AiNodeRunMapper.class),mock(AiRunInterventionMapper.class),mock(RunEventService.class));AiRun run=new AiRun();run.setId("r");run.setTenantId("0");assertFalse(service.available(run));}
}
