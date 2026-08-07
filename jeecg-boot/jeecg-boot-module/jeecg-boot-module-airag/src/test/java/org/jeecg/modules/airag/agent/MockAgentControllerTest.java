package org.jeecg.modules.airag.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.airag.agent.controller.MockAgentController;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockAgentControllerTest {
    @Test
    void exposesJeecgWrapperAndDefaultConnectorPointers() {
        ObjectMapper mapper = new ObjectMapper();
        AiConfigDtos.MockAgentRequest request = new AiConfigDtos.MockAgentRequest();
        request.setRequestId("request-1");
        request.setAgentCode("mockAgent");
        request.setInput(mapper.createObjectNode().put("task", "check"));

        Result<AiConfigDtos.AgentExecutionResult> result = new MockAgentController(mapper).execute(request);

        assertTrue(result.isSuccess());
        assertInstanceOf(AiConfigDtos.MockAgentResult.class, result);
        AiConfigDtos.MockAgentResult response = (AiConfigDtos.MockAgentResult) result;
        assertEquals("request-1", response.getOutput().path("requestId").asText());
        assertEquals("Mock agent completed", response.getSummary());
        assertTrue(response.getResult().isSuccess());
    }
}
