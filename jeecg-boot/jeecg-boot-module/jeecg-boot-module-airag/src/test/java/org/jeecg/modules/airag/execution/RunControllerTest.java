package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.controller.AiRunController;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.*;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.service.PipelineAccessContextFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RunControllerTest {
    @Test void publicStartAlwaysUsesJeecgSource(){RunStartService starts=mock(RunStartService.class);PipelineAccessContextFactory contexts=mock(PipelineAccessContextFactory.class);AgentAccessContext context=new AgentAccessContext("admin","0");when(contexts.current()).thenReturn(context);when(starts.startPipeline(any(),eq(context),any())).thenReturn(new RunCreateResult("r",RunStatus.CREATED,false));AiRunController controller=new AiRunController(starts,mock(RunOperationService.class),mock(RunQueryService.class),contexts);RunCreateRequest request=new RunCreateRequest();request.setRequestId("request");request.setRunType(RunType.PIPELINE);request.setPipelineId("p");request.setInput(new ObjectMapper().createObjectNode());
        assertEquals("r",controller.start(request).getResult().runId());verify(starts).startPipeline(eq(request),eq(context),argThat(source->source.source()==RunSource.JEECG));}
}
