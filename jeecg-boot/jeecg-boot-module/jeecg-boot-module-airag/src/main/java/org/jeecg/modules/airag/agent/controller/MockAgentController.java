package org.jeecg.modules.airag.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/mock-agent")
@ConditionalOnProperty(prefix = "ai.agent", name = "mock-enabled", havingValue = "true")
public class MockAgentController {
    private final ObjectMapper objectMapper;

    public MockAgentController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping("/execute")
    @IgnoreAuth
    public Result<AiConfigDtos.AgentExecutionResult> execute(@RequestBody AiConfigDtos.MockAgentRequest request) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("requestId", request.getRequestId());
        output.put("agentCode", request.getAgentCode());
        output.set("echo", request.getInput() == null ? NullNode.getInstance() : request.getInput());
        AiConfigDtos.AgentExecutionResult result = new AiConfigDtos.AgentExecutionResult();
        result.setSuccess(true);
        result.setOutput(output);
        result.setSummary("Mock agent completed");
        // Keep JEECG's wrapper while mirroring the stable connector fields at the root for default JSON Pointers.
        AiConfigDtos.MockAgentResult response = new AiConfigDtos.MockAgentResult();
        response.setSuccess(true);
        response.setCode(200);
        response.setResult(result);
        response.setOutput(output);
        response.setSummary(result.getSummary());
        return response;
    }
}
