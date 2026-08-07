package org.jeecg.modules.airag.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.jeecg.modules.airag.pipeline.entity.*;
import org.jeecg.modules.airag.pipeline.mapper.*;
import org.jeecg.modules.airag.pipeline.service.PipelinePermissionService;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineBotResolver;
import org.jeecg.modules.airag.pipeline.service.impl.PublishedPipelineProviderImpl;
import org.jeecg.modules.airag.pipeline.validation.TriggerKeyNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizedPipelineResolverTest {
    @Test
    void triggerResolutionEnforcesBotAndCurrentPublishedVersion() {
        AiPipelineMapper pipelines = mock(AiPipelineMapper.class);
        AiPipelineVersionMapper versions = mock(AiPipelineVersionMapper.class);
        AiPipelineTriggerKeyMapper triggers = mock(AiPipelineTriggerKeyMapper.class);
        PipelinePermissionService permission = mock(PipelinePermissionService.class);
        AiPipelineTriggerKey key = new AiPipelineTriggerKey(); key.setPipelineId("p"); key.setPublishedVersionId("v1");
        AiPipeline pipeline = new AiPipeline(); pipeline.setId("p"); pipeline.setEnabled(true); pipeline.setNotificationBotId("bot"); pipeline.setLatestVersionId("v1");
        AiPipelineVersion version = new AiPipelineVersion(); version.setId("v1"); version.setPipelineId("p"); version.setTenantId("0"); version.setVersion(1); version.setDefinitionHash("h"); version.setDefinitionJson("{\"schemaVersion\":\"1.1\",\"pipeline\":{\"notificationBotId\":\"bot\"},\"nodes\":[],\"edges\":[]}");
        when(triggers.selectByNormalizedValue("0", "alias")).thenReturn(key);
        when(permission.requireVisible("p", new AgentAccessContext("admin", "0"))).thenReturn(pipeline);
        when(versions.selectById("v1")).thenReturn(version);
        PublishedPipelineProviderImpl resolver = new PublishedPipelineProviderImpl(pipelines, versions, triggers,
                permission, new PipelineDefinitionCodec(new ObjectMapper()), new TriggerKeyNormalizer(),
                mock(AuthorizedAgentConfigProvider.class), mock(AuthorizedPipelineBotResolver.class));

        assertEquals("v1", resolver.resolveEnabledByTriggerKey("bot", "Alias", new AgentAccessContext("admin", "0")).getVersionId());
    }
}
