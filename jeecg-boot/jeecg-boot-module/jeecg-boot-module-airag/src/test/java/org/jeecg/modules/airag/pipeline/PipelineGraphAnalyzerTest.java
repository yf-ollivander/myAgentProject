package org.jeecg.modules.airag.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.validation.PipelineGraphAnalyzer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PipelineGraphAnalyzerTest {
    private final PipelineDefinitionCodec codec = new PipelineDefinitionCodec(new ObjectMapper());
    private final PipelineGraphAnalyzer analyzer = new PipelineGraphAnalyzer();

    @Test
    void computesDagReachabilityTerminationAndDominators() throws Exception {
        PipelineDefinition definition = codec.readDefinition(resource("simple-serial.json"));
        PipelineGraphAnalyzer.Analysis result = analyzer.analyze(definition);
        assertTrue(result.dag());
        assertEquals(definition.getNodes().size(), result.reachable().size());
        assertEquals(definition.getNodes().size(), result.canTerminate().size());
        assertTrue(result.dominators().get("end").contains("worker"));
    }

    @Test
    void rejectsRepairEdgesThatReturnToAnAncestor() throws Exception {
        PipelineDefinition definition = codec.readDefinition(resource("fixed-repair.json"));
        PipelineEdge back = new PipelineEdge();
        back.setId("back"); back.setSource("retest"); back.setTarget("test"); back.setBranch(PipelineEnums.EdgeBranch.DEFAULT);
        definition.getEdges().add(back);
        assertFalse(analyzer.analyze(definition).dag());
    }

    private String resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/pipeline-definition/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
