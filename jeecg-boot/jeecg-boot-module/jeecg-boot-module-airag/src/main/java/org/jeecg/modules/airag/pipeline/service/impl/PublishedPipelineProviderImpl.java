package org.jeecg.modules.airag.pipeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.common.config.TenantContext;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
import org.jeecg.modules.airag.pipeline.contract.AgentNodeConfig;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.jeecg.modules.airag.pipeline.contract.PipelineEnums;
import org.jeecg.modules.airag.pipeline.contract.PublishedPipelineSnapshot;
import org.jeecg.modules.airag.pipeline.dto.PipelineDtos;
import org.jeecg.modules.airag.pipeline.entity.AiPipeline;
import org.jeecg.modules.airag.pipeline.entity.AiPipelineTriggerKey;
import org.jeecg.modules.airag.pipeline.entity.AiPipelineVersion;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineMapper;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineTriggerKeyMapper;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineVersionMapper;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineResolver;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineBotResolver;
import org.jeecg.modules.airag.pipeline.service.PipelinePermissionService;
import org.jeecg.modules.airag.pipeline.service.PublishedPipelineProvider;
import org.jeecg.modules.airag.pipeline.validation.PipelineErrorCode;
import org.jeecg.modules.airag.pipeline.validation.PipelineException;
import org.jeecg.modules.airag.pipeline.validation.TriggerKeyNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Supplier;

@Service
public class PublishedPipelineProviderImpl implements PublishedPipelineProvider, AuthorizedPipelineResolver {
    private final AiPipelineMapper pipelineMapper;
    private final AiPipelineVersionMapper versionMapper;
    private final AiPipelineTriggerKeyMapper triggerMapper;
    private final PipelinePermissionService permissionService;
    private final PipelineDefinitionCodec codec;
    private final TriggerKeyNormalizer triggerNormalizer;
    private final AuthorizedAgentConfigProvider agentProvider;
    private final AuthorizedPipelineBotResolver botResolver;

    public PublishedPipelineProviderImpl(AiPipelineMapper pipelineMapper, AiPipelineVersionMapper versionMapper,
                                         AiPipelineTriggerKeyMapper triggerMapper,
                                         PipelinePermissionService permissionService,
                                         PipelineDefinitionCodec codec,
                                         TriggerKeyNormalizer triggerNormalizer,
                                         AuthorizedAgentConfigProvider agentProvider,
                                         AuthorizedPipelineBotResolver botResolver) {
        this.pipelineMapper = pipelineMapper;
        this.versionMapper = versionMapper;
        this.triggerMapper = triggerMapper;
        this.permissionService = permissionService;
        this.codec = codec;
        this.triggerNormalizer = triggerNormalizer;
        this.agentProvider = agentProvider;
        this.botResolver = botResolver;
    }

    @Override
    public PublishedPipelineSnapshot getAuthorizedVersionById(String versionId, AgentAccessContext context) {
        AiPipelineVersion version = versionMapper.selectById(versionId);
        if (version == null || context == null || !context.tenantId().equals(version.getTenantId())) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_VERSION_NOT_FOUND,
                "Published pipeline version was not found", versionId);
        }
        // A version ID is not an authorization boundary; visibility belongs to its owning pipeline.
        permissionService.requireVisible(version.getPipelineId(), context);
        return snapshot(version);
    }

    @Override
    public PublishedPipelineSnapshot resolveEnabledForRun(String pipelineId, AgentAccessContext context) {
        AiPipeline pipeline = permissionService.requireVisible(pipelineId, context);
        if (!Boolean.TRUE.equals(pipeline.getEnabled())) throw PipelineException.of(PipelineErrorCode.PIPELINE_DISABLED,
                "Pipeline is disabled", pipelineId);
        if (pipeline.getLatestVersionId() == null) throw PipelineException.of(PipelineErrorCode.PIPELINE_NOT_PUBLISHED,
                "Pipeline has no published version", pipelineId);
        PublishedPipelineSnapshot snapshot = getAuthorizedVersionById(pipeline.getLatestVersionId(), context);
        validateCurrentDependencies(snapshot, context);
        return snapshot;
    }

    @Override
    public List<PipelineDtos.PipelineOption> listEnabledOptions(String keyword, int limit,
                                                                AgentAccessContext context) {
        if (limit < 1 || limit > 200) throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                "Pipeline option limit must be between 1 and 200", limit);
        return withTenant(context.tenantId(), () -> {
            QueryWrapper<AiPipeline> query = permissionService.authorizedQuery(context);
            query.lambda().eq(AiPipeline::getEnabled, true).isNotNull(AiPipeline::getLatestVersionId)
                    .and(StringUtils.hasText(keyword), q -> q.like(AiPipeline::getPipelineCode, keyword)
                            .or().like(AiPipeline::getName, keyword))
                    .orderByAsc(AiPipeline::getName).last("LIMIT " + limit);
            return pipelineMapper.selectList(query).stream().map(pipeline -> new PipelineDtos.PipelineOption(
                    pipeline.getId(), pipeline.getPipelineCode(), pipeline.getName(), pipeline.getLatestVersion(),
                    pipeline.getLatestVersionId())).toList();
        });
    }

    @Override
    public PublishedPipelineSnapshot resolveEnabledByTriggerKey(String botId, String triggerKey,
                                                                 AgentAccessContext context) {
        String normalized = triggerNormalizer.normalize(triggerKey);
        AiPipelineTriggerKey key = withTenant(context.tenantId(),
                () -> triggerMapper.selectByNormalizedValue(context.tenantId(), normalized));
        if (key == null) throw PipelineException.of(PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                "Pipeline trigger was not found or is forbidden", normalized);
        AiPipeline pipeline = permissionService.requireVisible(key.getPipelineId(), context);
        if (!Boolean.TRUE.equals(pipeline.getEnabled())) throw PipelineException.of(PipelineErrorCode.PIPELINE_DISABLED,
                "Pipeline is disabled", pipeline.getId());
        if (!botId.equals(pipeline.getNotificationBotId()) || !key.getPublishedVersionId().equals(pipeline.getLatestVersionId())) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                    "Pipeline trigger is not available for this bot", normalized);
        }
        PublishedPipelineSnapshot snapshot = getAuthorizedVersionById(key.getPublishedVersionId(), context);
        validateCurrentDependencies(snapshot, context);
        return snapshot;
    }

    private void validateCurrentDependencies(PublishedPipelineSnapshot snapshot, AgentAccessContext context) {
        // Published snapshots are immutable history, so current availability must be checked separately before a run.
        snapshot.getDefinition().getNodes().stream()
                .filter(node -> node != null && node.getType() == PipelineEnums.NodeType.AGENT)
                .map(node -> codec.parseNodeConfig(node, AgentNodeConfig.class))
                .forEach(config -> {
                    AgentConfigSnapshot agentSnapshot = agentProvider.resolveEnabledSnapshot(config.getAgentId(), context);
                    if (agentSnapshot.getConnector() == null
                            || !"1.1".equals(agentSnapshot.getConnector().getResultContractVersion())) {
                        throw PipelineException.of(PipelineErrorCode.PIPELINE_AGENT_NOT_AVAILABLE,
                                "Pipeline Agent is not available", config.getAgentId());
                    }
                });
        String botId = snapshot.getDefinition().getPipeline().getNotificationBotId();
        botResolver.resolveAvailable(botId, context);
    }

    private PublishedPipelineSnapshot snapshot(AiPipelineVersion version) {
        return PublishedPipelineSnapshot.builder().pipelineId(version.getPipelineId()).versionId(version.getId())
                .version(version.getVersion()).tenantId(version.getTenantId()).definitionHash(version.getDefinitionHash())
                .definition(codec.readDefinition(version.getDefinitionJson())).build();
    }

    private <T> T withTenant(String tenantId, Supplier<T> action) {
        String previous = TenantContext.getTenant();
        try {
            TenantContext.setTenant(tenantId);
            return action.get();
        } finally {
            if (StringUtils.hasText(previous)) TenantContext.setTenant(previous); else TenantContext.clear();
        }
    }
}
