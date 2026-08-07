package org.jeecg.modules.airag.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.collaboration.feishu.FeishuCardSigner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeishuCardSignerTest {
    @Test
    void matchesFixedHmacVectorAndRejectsTampering() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setSecretKey("12345678901234567890123456789012");
        ObjectMapper mapper = new ObjectMapper();
        FeishuCardSigner signer = new FeishuCardSigner(properties, mapper);
        ObjectNode value = mapper.createObjectNode();
        value.put("action", "SUPPLY_INPUT"); value.put("botId", "b"); value.put("interventionId", "i");
        value.put("resumeToken", "t"); value.put("runId", "r");
        assertEquals("c09240fc4342acbd631613c2c9ee5532e7e928d6da5c3a660af6d791f1bc2bab", signer.sign(value));
        value.put("signature", signer.sign(value));
        assertTrue(signer.verify(value));
        value.put("runId", "other");
        assertFalse(signer.verify(value));
    }
}
