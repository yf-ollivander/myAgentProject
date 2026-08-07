package org.jeecg.modules.airag.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineBotResolver;
import org.jeecg.modules.airag.pipeline.validation.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PipelineDefinitionValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PipelineDefinitionCodec codec = new PipelineDefinitionCodec(objectMapper);
    private final PipelineNodeConfigParser parser = new PipelineNodeConfigParser(codec);
    private final AuthorizedAgentConfigProvider agents = mock(AuthorizedAgentConfigProvider.class);
    private final AuthorizedPipelineBotResolver bots = mock(AuthorizedPipelineBotResolver.class);
    private final PipelineDefinitionValidator validator = new PipelineDefinitionValidator(codec, parser,
            new PipelineGraphAnalyzer(), new TriggerKeyNormalizer(), agents, bots);

    @Test
    void allFiveFixturesPassFullValidation() throws Exception {
        when(agents.resolveEnabledSnapshot(anyString(), any())).thenReturn(AgentConfigSnapshot.builder().agentId("a").build());
        when(bots.resolveAvailable(anyString(), any())).thenReturn(new AiFeishuBot());
        for (String fixture : List.of("simple-serial.json", "condition-branches.json", "five-role-artifacts.json",
                "needs-input.json", "fixed-repair.json")) {
            PipelineDefinition definition = codec.readDefinition(resource(fixture));
            List<ValidationIssue> issues = validator.validate(definition, ui(definition),
                    new AgentAccessContext("admin", "0"), true);
            assertTrue(issues.isEmpty(), fixture + ": " + issues);
        }
    }

    @Test
    void scriptLikeTemplateAndNonDominatingReferenceAreRejected() throws Exception {
        PipelineDefinition definition = codec.readDefinition(resource("condition-branches.json"));
        definition.getPipeline().setFinalSummaryTemplate("{{T(java.lang.Runtime).exec('bad')}}");
        List<ValidationIssue> issues = validator.validate(definition, ui(definition), null, false);
        assertTrue(issues.stream().anyMatch(issue -> issue.getCode().startsWith("TEMPLATE")));
    }

    private PipelineUiModel ui(PipelineDefinition definition) {
        PipelineUiModel ui = new PipelineUiModel();
        ui.setNodes(definition.getNodes().stream().map(node -> { PipelineUiModel.UiNode position = new PipelineUiModel.UiNode(); position.setId(node.getId()); position.setX(1D); position.setY(1D); return position; }).toList());
        PipelineUiModel.Viewport viewport = new PipelineUiModel.Viewport(); viewport.setX(0D); viewport.setY(0D); viewport.setZoom(1D); ui.setViewport(viewport);
        return ui;
    }

    private String resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/pipeline-definition/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
