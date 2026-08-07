package org.jeecg.modules.airag.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinition;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.jeecg.modules.airag.pipeline.validation.PipelineException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineDefinitionSerializationTest {
    private final PipelineDefinitionCodec codec = new PipelineDefinitionCodec(new ObjectMapper());

    @Test
    void allContractFixturesUseSchema11AndContainNoDraftSnapshot() throws Exception {
        for (String fixture : List.of("simple-serial.json", "condition-branches.json", "five-role-artifacts.json",
                "needs-input.json", "fixed-repair.json")) {
            PipelineDefinition definition = codec.readDefinition(resource(fixture));
            assertEquals("1.1", definition.getSchemaVersion());
            assertFalse(codec.write(definition).contains("agentSnapshot"));
        }
    }

    @Test
    void unknownDefinitionFieldsAreRejected() {
        assertThrows(PipelineException.class, () -> codec.readDefinition(
                "{\"schemaVersion\":\"1.1\",\"pipeline\":null,\"nodes\":[],\"edges\":[],\"script\":\"bad\"}"));
    }

    private String resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/pipeline-definition/" + name)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
