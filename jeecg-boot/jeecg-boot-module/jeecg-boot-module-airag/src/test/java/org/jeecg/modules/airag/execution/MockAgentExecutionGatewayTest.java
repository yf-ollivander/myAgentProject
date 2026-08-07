package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.execution.gateway.*;
import org.jeecg.modules.airag.pipeline.contract.PipelineEnums;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MockAgentExecutionGatewayTest {
    @Test void stableRequestProducesDeterministicNeedsInput()throws Exception{var input=new ObjectMapper().readTree("{\"__mockStatus\":\"NEEDS_INPUT\",\"__mockUserPrompt\":\"clarify\"}");var request=new AgentExecutionGateway.AgentExecutionRequest("r:n:0:0","r","nr",0,0,"trace",null,input,null,List.of());var result=new MockAgentExecutionGateway().execute(request);assertEquals(PipelineEnums.AgentResultStatus.NEEDS_INPUT,result.getStatus());assertTrue(result.getNeedsUser());assertFalse(result.getRetryable());assertEquals("clarify",result.getUserPrompt());}
}
