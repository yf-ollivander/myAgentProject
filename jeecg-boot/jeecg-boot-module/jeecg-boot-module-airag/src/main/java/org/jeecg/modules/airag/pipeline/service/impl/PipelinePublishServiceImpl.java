package org.jeecg.modules.airag.pipeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
import org.jeecg.modules.airag.agent.support.AgentConfigSnapshotSanitizer;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.dto.PipelineDtos;
import org.jeecg.modules.airag.pipeline.entity.AiPipeline;
import org.jeecg.modules.airag.pipeline.entity.AiPipelineTriggerKey;
import org.jeecg.modules.airag.pipeline.entity.AiPipelineVersion;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineMapper;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineTriggerKeyMapper;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineVersionMapper;
import org.jeecg.modules.airag.pipeline.service.*;
import org.jeecg.modules.airag.pipeline.validation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class PipelinePublishServiceImpl implements PipelinePublishService {
    private final AiPipelineMapper pipelineMapper;
    private final AiPipelineVersionMapper versionMapper;
    private final AiPipelineTriggerKeyMapper triggerMapper;
    private final PipelinePermissionService permissionService;
    private final PipelineDefinitionCodec codec;
    private final PipelineNodeConfigParser nodeParser;
    private final PipelineDefinitionValidator validator;
    private final PipelineDefinitionNormalizer normalizer;
    private final TriggerKeyNormalizer triggerNormalizer;
    private final AuthorizedAgentConfigProvider agentProvider;
    private final AuthorizedPipelineBotResolver botResolver;
    private final AgentConfigSnapshotSanitizer snapshotSanitizer;

    public PipelinePublishServiceImpl(AiPipelineMapper pipelineMapper, AiPipelineVersionMapper versionMapper,
                                      AiPipelineTriggerKeyMapper triggerMapper,
                                      PipelinePermissionService permissionService, PipelineDefinitionCodec codec,
                                      PipelineNodeConfigParser nodeParser, PipelineDefinitionValidator validator,
                                      PipelineDefinitionNormalizer normalizer, TriggerKeyNormalizer triggerNormalizer,
                                      AuthorizedAgentConfigProvider agentProvider,
                                      AuthorizedPipelineBotResolver botResolver,
                                      AgentConfigSnapshotSanitizer snapshotSanitizer) {
        this.pipelineMapper = pipelineMapper;
        this.versionMapper = versionMapper;
        this.triggerMapper = triggerMapper;
        this.permissionService = permissionService;
        this.codec = codec;
        this.nodeParser = nodeParser;
        this.validator = validator;
        this.normalizer = normalizer;
        this.triggerNormalizer = triggerNormalizer;
        this.agentProvider = agentProvider;
        this.botResolver = botResolver;
        this.snapshotSanitizer = snapshotSanitizer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PipelineDtos.PublishResult publish(String pipelineId, PipelineDtos.PublishRequest request,
                                               AgentAccessContext context) {
        // Permission is resolved before the lower-level row lock so the lock cannot become an IDOR bypass.
        permissionService.requireVisible(pipelineId, context);
        AiPipeline pipeline = pipelineMapper.selectByIdForUpdate(pipelineId, context.tenantId());
        if (pipeline == null) throw notFound();

        AiPipelineVersion requestVersion = versionMapper.selectOne(new LambdaQueryWrapper<AiPipelineVersion>()
                .eq(AiPipelineVersion::getPipelineId, pipelineId)
                .eq(AiPipelineVersion::getPublishRequestId, request.getRequestId()));
        if (requestVersion != null) return result(requestVersion, true);
        if (!request.getDraftRevision().equals(pipeline.getDraftRevision())) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DRAFT_CONFLICT,
                    "Draft revision changed before publication", request.getDraftRevision());
        }

        PipelineDefinition draft = codec.readDefinition(pipeline.getDraftDefinitionJson());
        PipelineUiModel ui = codec.readUi(pipeline.getDraftUiJson());
        validator.validateOrThrow(draft, ui, context, true);
        botResolver.resolveAvailable(draft.getPipeline().getNotificationBotId(), context);

        PipelineDefinition published = codec.readDefinition(codec.write(draft));
        for (PipelineNode node : published.getNodes()) {
            if (node.getType() != PipelineEnums.NodeType.AGENT) continue;
            AgentNodeConfig config = nodeParser.parse(node, AgentNodeConfig.class);
            AgentConfigSnapshot snapshot = snapshotSanitizer.sanitize(resolveAgentSnapshot(config.getAgentId(), context));
            config.setAgentSnapshot(snapshot);
            node.setConfig(codec.valueToTree(config));
        }
        PipelineDefinitionNormalizer.NormalizedDefinition normalized = normalizer.normalize(published);
        if (normalized.json().getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Published definition with Agent snapshots exceeds 1 MiB", null);
        }

        AiPipelineVersion latest = pipeline.getLatestVersionId() == null ? null : versionMapper.selectById(pipeline.getLatestVersionId());
        if (latest != null && latest.getSourceDraftRevision().equals(pipeline.getDraftRevision())
                && latest.getDefinitionHash().equals(normalized.hash())) return result(latest, true);

        AiPipelineVersion version = new AiPipelineVersion();
        version.setPipelineId(pipelineId);
        version.setTenantId(context.tenantId());
        version.setVersion(pipeline.getLatestVersion() + 1);
        version.setSourceDraftRevision(pipeline.getDraftRevision());
        version.setSchemaVersion("1.1");
        version.setDefinitionJson(normalized.json());
        version.setUiJson(codec.write(ui));
        version.setDefinitionHash(normalized.hash());
        version.setPublishRequestId(request.getRequestId());
        version.setPublishedBy(context.username());
        version.setPublishedAt(new Date());
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException duplicate) {
            AiPipelineVersion existing = versionMapper.selectOne(new LambdaQueryWrapper<AiPipelineVersion>()
                    .eq(AiPipelineVersion::getPipelineId, pipelineId)
                    .eq(AiPipelineVersion::getPublishRequestId, request.getRequestId()));
            if (existing != null) return result(existing, true);
            throw PipelineException.of(PipelineErrorCode.PIPELINE_PUBLISH_CONFLICT,
                    "Concurrent publication conflicted", request.getRequestId());
        }

        // Replacing the registry in the same transaction prevents old aliases from routing to the new version.
        triggerMapper.deleteByPipelineId(pipelineId);
        try {
            insertTrigger(pipeline, version, PipelineEnums.TriggerKeyType.CODE, draft.getPipeline().getCode(), context.username());
            for (String alias : draft.getPipeline().getTriggerAliases()) {
                insertTrigger(pipeline, version, PipelineEnums.TriggerKeyType.ALIAS, alias, context.username());
            }
        } catch (DuplicateKeyException duplicate) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_TRIGGER_KEY_CONFLICT,
                    "Pipeline code or alias conflicts with another pipeline", null);
        }
        pipelineMapper.updatePublishedState(pipelineId, context.tenantId(), version.getVersion(), version.getId(),
                draft.getPipeline().getNotificationBotId(), draft.getPipeline().getDefaultFeishuChatId(), context.username());
        return result(version, false);
    }

    private void insertTrigger(AiPipeline pipeline, AiPipelineVersion version, PipelineEnums.TriggerKeyType type,
                               String displayValue, String username) {
        AiPipelineTriggerKey key = new AiPipelineTriggerKey();
        key.setTenantId(pipeline.getTenantId());
        key.setPipelineId(pipeline.getId());
        key.setPublishedVersionId(version.getId());
        key.setKeyType(type.name());
        key.setDisplayValue(displayValue);
        key.setNormalizedValue(triggerNormalizer.normalize(displayValue));
        key.setCreateBy(username);
        key.setCreateTime(new Date());
        triggerMapper.insert(key);
    }

    private AgentConfigSnapshot resolveAgentSnapshot(String agentId, AgentAccessContext context) {
        try {
            return agentProvider.resolveEnabledSnapshot(agentId, context);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null && (message.contains("not authorized") || message.contains("Authorized JEECG user"))) {
                throw PipelineException.of(PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                        "Pipeline dependency is outside the authorized data scope", null);
            }
            throw PipelineException.of(PipelineErrorCode.PIPELINE_AGENT_NOT_AVAILABLE,
                    "Agent became unavailable during publication", agentId);
        }
    }

    private PipelineDtos.PublishResult result(AiPipelineVersion version, boolean reused) {
        return new PipelineDtos.PublishResult(version.getId(), version.getVersion(), version.getDefinitionHash(), reused);
    }

    private PipelineException notFound() {
        return PipelineException.of(PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                "Pipeline was not found or is outside the authorized data scope", null);
    }
}
