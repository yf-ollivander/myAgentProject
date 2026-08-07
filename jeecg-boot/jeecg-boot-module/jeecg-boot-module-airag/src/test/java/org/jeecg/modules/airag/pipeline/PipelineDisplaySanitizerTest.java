package org.jeecg.modules.airag.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.jeecg.modules.airag.pipeline.validation.PipelineDisplaySanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineDisplaySanitizerTest {
    @Test
    void removesSensitiveSnapshotFieldsAndKeepsAgentReference() {
        String json = "{\"nodes\":[{\"config\":{\"agentSnapshot\":{\"agentId\":\"1\",\"agentCode\":\"a\",\"name\":\"Agent\",\"systemPrompt\":\"secret\",\"connector\":{\"baseUrl\":\"https://internal\",\"requestHeaders\":{\"Authorization\":\"x\"}}}}}]}";
        JsonNode value = new PipelineDisplaySanitizer(new PipelineDefinitionCodec(new ObjectMapper())).sanitize(json);
        String display = value.toString();
        assertTrue(display.contains("agentRef"));
        assertFalse(display.contains("systemPrompt"));
        assertFalse(display.contains("baseUrl"));
        assertFalse(display.contains("Authorization"));
    }
}
