package org.jeecg.modules.airag.pipeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
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
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AiPipelineServiceImpl extends ServiceImpl<AiPipelineMapper, AiPipeline> implements IAiPipelineService {
    private static final Set<String> SENSITIVE_FIELDS = Set.of("agentsnapshot", "secret", "authorization",
            "appsecret", "encryptkey", "verificationtoken", "secretcipher");
    private final AiPipelineVersionMapper versionMapper;
    private final AiPipelineTriggerKeyMapper triggerMapper;
    private final PipelinePermissionService permissionService;
    private final PipelineAccessContextFactory contextFactory;
    private final PipelineDefinitionCodec codec;
    private final PipelineDefinitionValidator validator;
    private final PipelineDisplaySanitizer sanitizer;
    private final PipelinePublishService publishService;
    private final AuthorizedPipelineResolver resolver;
    private final AuthorizedAgentConfigProvider agentProvider;
    private final AuthorizedPipelineBotResolver botResolver;
    private final PipelineNodeConfigParser nodeParser;
    private final TriggerKeyNormalizer triggerNormalizer;

    public AiPipelineServiceImpl(AiPipelineVersionMapper versionMapper, AiPipelineTriggerKeyMapper triggerMapper,
                                 PipelinePermissionService permissionService,
                                 PipelineAccessContextFactory contextFactory, PipelineDefinitionCodec codec,
                                 PipelineDefinitionValidator validator, PipelineDisplaySanitizer sanitizer,
                                 PipelinePublishService publishService, AuthorizedPipelineResolver resolver,
                                 AuthorizedAgentConfigProvider agentProvider,
                                 AuthorizedPipelineBotResolver botResolver,
                                 PipelineNodeConfigParser nodeParser, TriggerKeyNormalizer triggerNormalizer) {
        this.versionMapper = versionMapper;
        this.triggerMapper = triggerMapper;
        this.permissionService = permissionService;
        this.contextFactory = contextFactory;
        this.codec = codec;
        this.validator = validator;
        this.sanitizer = sanitizer;
        this.publishService = publishService;
        this.resolver = resolver;
        this.agentProvider = agentProvider;
        this.botResolver = botResolver;
        this.nodeParser = nodeParser;
        this.triggerNormalizer = triggerNormalizer;
    }

    @Override
    public IPage<PipelineDtos.ListItem> page(String code, String name, Boolean enabled, int pageNo, int pageSize) {
        QueryWrapper<AiPipeline> query = new QueryWrapper<>();
        query.lambda().like(StringUtils.hasText(code), AiPipeline::getPipelineCode, code)
                .like(StringUtils.hasText(name), AiPipeline::getName, name)
                .eq(enabled != null, AiPipeline::getEnabled, enabled)
                .orderByDesc(AiPipeline::getUpdateTime).orderByDesc(AiPipeline::getCreateTime);
        QueryGenerator.installAuthMplus(query, AiPipeline.class);
        Page<AiPipeline> source = baseMapper.selectPage(new Page<>(pageNo, pageSize), query);
        Page<PipelineDtos.ListItem> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::listItem).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PipelineDtos.CreateResult create(PipelineDtos.CreateRequest request) {
        AgentAccessContext context = contextFactory.current();
        String code = triggerNormalizer.normalize(request.getCode());
        if (baseMapper.selectCount(new LambdaQueryWrapper<AiPipeline>()
                .eq(AiPipeline::getTenantId, context.tenantId()).eq(AiPipeline::getPipelineCode, code)) > 0) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_CODE_DUPLICATE,
                    "Pipeline code already exists", code);
        }
        AiPipelineTriggerKey occupied = triggerMapper.selectByNormalizedValue(context.tenantId(), code);
        if (occupied != null) throw PipelineException.of(PipelineErrorCode.PIPELINE_TRIGGER_KEY_CONFLICT,
                "Pipeline code conflicts with a published alias", code);
        PipelineDefinition definition = initialDefinition(code, request.getName());
        PipelineUiModel ui = initialUi();
        AiPipeline pipeline = new AiPipeline();
        pipeline.setTenantId(context.tenantId());
        pipeline.setPipelineCode(code);
        pipeline.setName(request.getName());
        pipeline.setDescription(request.getDescription());
        pipeline.setDraftDefinitionJson(codec.write(definition));
        pipeline.setDraftUiJson(codec.write(ui));
        pipeline.setDraftRevision(0L);
        pipeline.setLatestVersion(0);
        pipeline.setEnabled(false);
        pipeline.setDelFlag(0);
        try {
            save(pipeline);
        } catch (DuplicateKeyException duplicate) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_CODE_DUPLICATE,
                    "Pipeline code already exists", code);
        }
        return new PipelineDtos.CreateResult(pipeline.getId(), 0L);
    }

    @Override
    public PipelineDtos.Detail get(String id) {
        return detail(permissionService.requireVisibleCurrent(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AiPipeline pipeline = permissionService.requireVisibleCurrent(id);
        if (Boolean.TRUE.equals(pipeline.getEnabled()) || pipeline.getLatestVersion() != 0) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_PUBLISH_CONFLICT,
                    "Only disabled, unpublished pipelines can be deleted", id);
        }
        baseMapper.deleteById(pipeline.getId());
    }

    @Override
    public PipelineDtos.DraftView getDraft(String id) {
        AiPipeline pipeline = permissionService.requireVisibleCurrent(id);
        return new PipelineDtos.DraftView(pipeline.getDraftRevision(),
                codec.readDefinition(pipeline.getDraftDefinitionJson()), codec.readUi(pipeline.getDraftUiJson()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long saveDraft(String id, PipelineDtos.DraftSaveRequest request) {
        AiPipeline pipeline = permissionService.requireVisibleCurrent(id);
        rejectSensitive(request.getDefinition());
        PipelineDefinition definition = codec.readDefinition(request.getDefinition().toString());
        PipelineUiModel ui = codec.readUi(request.getUi().toString());
        validateDraftPayload(pipeline, definition, ui);
        AgentAccessContext context = contextFactory.current();
        int affected = baseMapper.updateDraft(id, context.tenantId(), request.getDraftRevision(),
                codec.write(definition), codec.write(ui), definition.getPipeline().getName(), context.username());
        if (affected == 0) throw PipelineException.of(PipelineErrorCode.PIPELINE_DRAFT_CONFLICT,
                "Draft was updated by another session", request.getDraftRevision());
        return request.getDraftRevision() + 1;
    }

    @Override
    public PipelineDtos.ValidationResult validate(String id, long revision) {
        AiPipeline pipeline = permissionService.requireVisibleCurrent(id);
        if (pipeline.getDraftRevision() != revision) throw PipelineException.of(PipelineErrorCode.PIPELINE_DRAFT_CONFLICT,
                "Draft revision changed before validation", revision);
        AgentAccessContext context = contextFactory.current();
        List<ValidationIssue> issues = validator.validate(codec.readDefinition(pipeline.getDraftDefinitionJson()),
                codec.readUi(pipeline.getDraftUiJson()), context, true);
        return PipelineDtos.ValidationResult.of(issues);
    }

    @Override
    public PipelineDtos.PublishResult publish(String id, PipelineDtos.PublishRequest request) {
        permissionService.requireVisibleCurrent(id);
        return publishService.publish(id, request, contextFactory.current());
    }

    @Override
    public IPage<PipelineDtos.VersionSummary> versions(String id, int pageNo, int pageSize) {
        AiPipeline pipeline = permissionService.requireVisibleCurrent(id);
        Page<AiPipelineVersion> source = versionMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<AiPipelineVersion>().eq(AiPipelineVersion::getPipelineId, pipeline.getId())
                        .eq(AiPipelineVersion::getTenantId, pipeline.getTenantId())
                        .orderByDesc(AiPipelineVersion::getVersion));
        Page<PipelineDtos.VersionSummary> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::versionSummary).toList());
        return result;
    }

    @Override
    public PipelineDtos.VersionView version(String id, int version) {
        AiPipeline pipeline = permissionService.requireVisibleCurrent(id);
        AiPipelineVersion entity = versionMapper.selectOne(new LambdaQueryWrapper<AiPipelineVersion>()
                .eq(AiPipelineVersion::getPipelineId, pipeline.getId())
                .eq(AiPipelineVersion::getTenantId, pipeline.getTenantId())
                .eq(AiPipelineVersion::getVersion, version));
        if (entity == null) throw PipelineException.of(PipelineErrorCode.PIPELINE_VERSION_NOT_FOUND,
                "Pipeline version was not found", version);
        return new PipelineDtos.VersionView(entity.getId(), entity.getVersion(), entity.getSchemaVersion(),
                sanitizer.sanitize(entity.getDefinitionJson()), codec.readUi(entity.getUiJson()),
                entity.getDefinitionHash(), entity.getPublishedBy(), entity.getPublishedAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        AiPipeline pipeline = permissionService.requireVisibleCurrent(id);
        if (pipeline.getLatestVersionId() == null) throw PipelineException.of(PipelineErrorCode.PIPELINE_NOT_PUBLISHED,
                "Pipeline must be published before it can be enabled", id);
        AgentAccessContext context = contextFactory.current();
        AiPipelineVersion version = versionMapper.selectById(pipeline.getLatestVersionId());
        PipelineDefinition definition = codec.readDefinition(version.getDefinitionJson());
        botResolver.resolveAvailable(definition.getPipeline().getNotificationBotId(), context);
        for (PipelineNode node : definition.getNodes()) {
            if (node.getType() == PipelineEnums.NodeType.AGENT) {
                String agentId = nodeParser.parse(node, AgentNodeConfig.class).getAgentId();
                try {
                    agentProvider.resolveEnabledSnapshot(agentId, context);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage();
                    if (message != null && (message.contains("not authorized") || message.contains("Authorized JEECG user"))) {
                        throw PipelineException.of(PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                                "Pipeline dependency is outside the authorized data scope", null);
                    }
                    throw PipelineException.of(PipelineErrorCode.PIPELINE_AGENT_NOT_AVAILABLE,
                            "Agent is unavailable; republish after restoring the dependency", agentId);
                }
            }
        }
        baseMapper.updateEnabled(id, context.tenantId(), true, context.username());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        permissionService.requireVisibleCurrent(id);
        AgentAccessContext context = contextFactory.current();
        baseMapper.updateEnabled(id, context.tenantId(), false, context.username());
    }

    @Override
    public List<PipelineDtos.PipelineOption> options(String keyword, int limit) {
        return resolver.listEnabledOptions(keyword, limit, contextFactory.current());
    }

    private void validateDraftPayload(AiPipeline pipeline, PipelineDefinition definition, PipelineUiModel ui) {
        if (definition.getPipeline() == null || !pipeline.getPipelineCode().equals(triggerNormalizer.normalize(definition.getPipeline().getCode()))) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Pipeline code is immutable", pipeline.getPipelineCode());
        }
        if (!StringUtils.hasText(definition.getPipeline().getName()) || definition.getPipeline().getName().length() > 100) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Pipeline name is required and limited to 100 characters", null);
        }
        if (codec.write(definition).getBytes(StandardCharsets.UTF_8).length > 1024 * 1024
                || codec.write(ui).getBytes(StandardCharsets.UTF_8).length > 512 * 1024) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Draft definition or UI exceeds the size limit", null);
        }
        Set<String> definitionIds = definition.getNodes().stream().map(PipelineNode::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> uiIds = ui.getNodes().stream().map(PipelineUiModel.UiNode::getId).collect(java.util.stream.Collectors.toSet());
        if (!definitionIds.equals(uiIds)) throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                "UI node IDs must match definition nodes", null);
    }

    private void rejectSensitive(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (SENSITIVE_FIELDS.contains(name.toLowerCase(Locale.ROOT))) {
                    throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                            "Draft contains a forbidden sensitive field", name);
                }
            }
        }
        node.elements().forEachRemaining(this::rejectSensitive);
    }

    private PipelineDefinition initialDefinition(String code, String name) {
        PipelineDefinition definition = new PipelineDefinition();
        definition.setSchemaVersion("1.1");
        PipelineDefinition.PipelineMetadata metadata = new PipelineDefinition.PipelineMetadata();
        metadata.setCode(code);
        metadata.setName(name);
        PipelineDefinition.InterventionPolicy policy = new PipelineDefinition.InterventionPolicy();
        policy.setOnAgentNeedsUser(PipelineEnums.ErrorPolicy.WAIT);
        policy.setOnRetriesExhausted(PipelineEnums.ErrorPolicy.FAIL);
        policy.setAllowedActions(List.of(PipelineEnums.InterventionAction.SUPPLY_INPUT,
                PipelineEnums.InterventionAction.RETRY, PipelineEnums.InterventionAction.CANCEL));
        metadata.setInterventionPolicy(policy);
        metadata.setFinalSummaryTemplate("");
        definition.setPipeline(metadata);
        PipelineNode start = new PipelineNode();
        start.setId("start"); start.setType(PipelineEnums.NodeType.START); start.setName("开始");
        start.setConfig(codec.valueToTree(new StartNodeConfig()));
        PipelineNode end = new PipelineNode();
        end.setId("end"); end.setType(PipelineEnums.NodeType.END); end.setName("结束");
        EndNodeConfig endConfig = new EndNodeConfig(); endConfig.setOutput(new LinkedHashMap<>()); endConfig.setCompletionSummary("");
        end.setConfig(codec.valueToTree(endConfig));
        definition.setNodes(new ArrayList<>(List.of(start, end)));
        PipelineEdge edge = new PipelineEdge();
        edge.setId("e_start_end"); edge.setSource("start"); edge.setTarget("end"); edge.setBranch(PipelineEnums.EdgeBranch.DEFAULT);
        definition.setEdges(new ArrayList<>(List.of(edge)));
        return definition;
    }

    private PipelineUiModel initialUi() {
        PipelineUiModel ui = new PipelineUiModel();
        PipelineUiModel.UiNode start = uiNode("start", 200, 300);
        PipelineUiModel.UiNode end = uiNode("end", 700, 300);
        ui.setNodes(new ArrayList<>(List.of(start, end)));
        PipelineUiModel.Viewport viewport = new PipelineUiModel.Viewport();
        viewport.setX(0D); viewport.setY(0D); viewport.setZoom(1D); ui.setViewport(viewport);
        return ui;
    }

    private PipelineUiModel.UiNode uiNode(String id, double x, double y) {
        PipelineUiModel.UiNode node = new PipelineUiModel.UiNode();
        node.setId(id); node.setX(x); node.setY(y); node.setWidth(180D); node.setHeight(72D);
        return node;
    }

    private PipelineDtos.ListItem listItem(AiPipeline pipeline) {
        PipelineDtos.ListItem item = new PipelineDtos.ListItem();
        item.setId(pipeline.getId()); item.setPipelineCode(pipeline.getPipelineCode()); item.setName(pipeline.getName());
        item.setDescription(pipeline.getDescription()); item.setDraftRevision(pipeline.getDraftRevision());
        item.setLatestVersion(pipeline.getLatestVersion()); item.setEnabled(Boolean.TRUE.equals(pipeline.getEnabled()));
        item.setUpdateTime(pipeline.getUpdateTime());
        return item;
    }

    private PipelineDtos.Detail detail(AiPipeline pipeline) {
        PipelineDtos.Detail detail = new PipelineDtos.Detail();
        PipelineDtos.ListItem item = listItem(pipeline);
        detail.setId(item.getId()); detail.setPipelineCode(item.getPipelineCode()); detail.setName(item.getName());
        detail.setDescription(item.getDescription()); detail.setDraftRevision(item.getDraftRevision());
        detail.setLatestVersion(item.getLatestVersion()); detail.setEnabled(item.isEnabled()); detail.setUpdateTime(item.getUpdateTime());
        detail.setNotificationBotId(pipeline.getNotificationBotId()); detail.setDefaultFeishuChatId(pipeline.getDefaultFeishuChatId());
        detail.setLatestVersionId(pipeline.getLatestVersionId());
        return detail;
    }

    private PipelineDtos.VersionSummary versionSummary(AiPipelineVersion version) {
        return new PipelineDtos.VersionSummary(version.getId(), version.getVersion(), version.getSourceDraftRevision(),
                version.getDefinitionHash(), version.getPublishedBy(), version.getPublishedAt());
    }
}
