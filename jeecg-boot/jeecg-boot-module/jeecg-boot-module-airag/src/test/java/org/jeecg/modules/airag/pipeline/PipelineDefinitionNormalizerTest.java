package org.jeecg.modules.airag.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinition;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.jeecg.modules.airag.pipeline.validation.PipelineDefinitionNormalizer;
import org.jeecg.modules.airag.pipeline.validation.PipelineNodeConfigParser;
import org.jeecg.modules.airag.pipeline.validation.TriggerKeyNormalizer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PipelineDefinitionNormalizerTest {
    @Test
    void hashIsStableAcrossNodeAndAliasOrdering() throws Exception {
        PipelineDefinitionCodec codec = new PipelineDefinitionCodec(new ObjectMapper());
        PipelineDefinitionNormalizer normalizer = new PipelineDefinitionNormalizer(codec, new PipelineNodeConfigParser(codec));
        PipelineDefinition first = codec.readDefinition(resource("simple-serial.json"));
        PipelineDefinition second = codec.readDefinition(codec.write(first));
        Collections.reverse(second.getNodes());
        Collections.reverse(second.getPipeline().getTriggerAliases());
        assertEquals(normalizer.normalize(first).hash(), normalizer.normalize(second).hash());
        assertEquals(64, normalizer.normalize(first).hash().length());
    }

    @Test
    void triggerKeysUseNfkcAndAsciiLowercase() {
        assertEquals("abc1", new TriggerKeyNormalizer().normalize("  ＡBC1  "));
    }

    private String resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/pipeline-definition/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
