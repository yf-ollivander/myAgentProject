package org.jeecg.modules.airag.agent;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.node.TextNode;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.support.FeishuInboundMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDtoSerializationTest {
    @Test
    void auditSerializationOmitsSecretsPromptsAndTestInput() {
        AiConfigDtos.ConnectorUpsertRequest connector = new AiConfigDtos.ConnectorUpsertRequest();
        connector.setConnectorCode("demo");
        connector.setSecret("connector-secret");
        String connectorJson = JSONObject.toJSONString(connector);
        assertFalse(connectorJson.contains("connector-secret"));

        AiConfigDtos.AgentUpsertRequest agent = new AiConfigDtos.AgentUpsertRequest();
        agent.setAgentCode("agentDemo");
        agent.setSystemPrompt("private-system-prompt");
        assertFalse(JSONObject.toJSONString(agent).contains("private-system-prompt"));

        AiConfigDtos.TestInput input = new AiConfigDtos.TestInput();
        input.setInput(TextNode.valueOf("private-test-input"));
        assertFalse(JSONObject.toJSONString(input).contains("private-test-input"));
        assertTrue(JSONObject.toJSONString(input).equals("{}"));

        AiConfigDtos.FeishuTestInput feishu = new AiConfigDtos.FeishuTestInput();
        feishu.setTestMessage("private-test-message");
        assertFalse(JSONObject.toJSONString(feishu).contains("private-test-message"));

        FeishuInboundMessage message = FeishuInboundMessage.builder()
                .botKey("bot").messageId("message-id").content("private-command-body").build();
        assertFalse(JSONObject.toJSONString(message).contains("private-command-body"));
        assertFalse(message.toString().contains("private-command-body"));
    }
}
