package org.jeecg.modules.airag.pipeline;

import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
import org.jeecg.modules.airag.agent.support.AgentConfigSnapshotSanitizer;
import org.jeecg.modules.airag.pipeline.dto.PipelineDtos;
import org.jeecg.modules.airag.pipeline.entity.AiPipeline;
import org.jeecg.modules.airag.pipeline.entity.AiPipelineVersion;
import org.jeecg.modules.airag.pipeline.mapper.*;
import org.jeecg.modules.airag.pipeline.service.*;
import org.jeecg.modules.airag.pipeline.service.impl.PipelinePublishServiceImpl;
import org.jeecg.modules.airag.pipeline.validation.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PipelinePublishServiceTest {
    @Test
    void repeatedRequestIdReturnsExistingVersionWithoutCreatingAnother() {
        AiPipelineMapper pipelines = mock(AiPipelineMapper.class);
        AiPipelineVersionMapper versions = mock(AiPipelineVersionMapper.class);
        PipelinePermissionService permission = mock(PipelinePermissionService.class);
        AiPipeline pipeline = new AiPipeline(); pipeline.setId("p"); pipeline.setTenantId("0");
        AiPipelineVersion existing = new AiPipelineVersion(); existing.setId("v1"); existing.setVersion(1); existing.setDefinitionHash("hash");
        when(permission.requireVisible(anyString(), any())).thenReturn(pipeline);
        when(pipelines.selectByIdForUpdate("p", "0")).thenReturn(pipeline);
        when(versions.selectOne(any())).thenReturn(existing);
        PipelinePublishService service = new PipelinePublishServiceImpl(pipelines, versions,
                mock(AiPipelineTriggerKeyMapper.class), permission, mock(org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec.class),
                mock(PipelineNodeConfigParser.class), mock(PipelineDefinitionValidator.class),
                mock(PipelineDefinitionNormalizer.class), new TriggerKeyNormalizer(),
                mock(AuthorizedAgentConfigProvider.class), mock(AuthorizedPipelineBotResolver.class),
                new AgentConfigSnapshotSanitizer());
        PipelineDtos.PublishRequest request = new PipelineDtos.PublishRequest(); request.setDraftRevision(0L); request.setRequestId("request-1");

        PipelineDtos.PublishResult result = service.publish("p", request, new AgentAccessContext("admin", "0"));

        assertTrue(result.reused());
        assertEquals("v1", result.versionId());
        verify(versions, never()).insert(any(AiPipelineVersion.class));
    }

    @Test
    void publishedSnapshotRemovesCredentialLikeRequestHeaders() {
        AgentConfigSnapshot snapshot = AgentConfigSnapshot.builder().agentId("a").connector(
                AgentConfigSnapshot.ConnectorSnapshot.builder().connectorId("c")
                        .requestHeaders(Map.of("Authorization", "Bearer secret", "X-Trace", "visible"))
                        .secretConfigured(true).build()).build();

        AgentConfigSnapshot sanitized = new AgentConfigSnapshotSanitizer().sanitize(snapshot);

        assertNotNull(sanitized);
        assertFalse(sanitized.getConnector().getRequestHeaders().containsKey("Authorization"));
        assertEquals("visible", sanitized.getConnector().getRequestHeaders().get("X-Trace"));
    }
}
