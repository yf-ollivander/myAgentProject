package org.jeecg.modules.airag.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.BusinessNotificationEvent;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinition;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class RunNotificationViewProviderImpl implements RunNotificationViewProvider {
    private final AiRunMapper runMapper;
    private final AiRunInterventionMapper interventionMapper;
    private final AiNodeRunMapper nodeMapper;
    private final AiArtifactMapper artifactMapper;
    private final PipelineDefinitionCodec codec;
    private final ObjectMapper mapper;

    public RunNotificationViewProviderImpl(AiRunMapper runMapper, AiRunInterventionMapper interventionMapper,
                                           AiNodeRunMapper nodeMapper, AiArtifactMapper artifactMapper,
                                           PipelineDefinitionCodec codec, ObjectMapper mapper) {
        this.runMapper = runMapper; this.interventionMapper = interventionMapper;
        this.nodeMapper = nodeMapper; this.artifactMapper = artifactMapper; this.codec = codec; this.mapper = mapper;
    }

    @Override
    public RunCollaborationState state(String runId, AgentAccessContext context) {
        AiRun run = runMapper.selectOne(new QueryWrapper<AiRun>().lambda().eq(AiRun::getId, runId)
                .eq(AiRun::getTenantId, context.tenantId()).eq(AiRun::getInitiatorUsername, context.username())
                .eq(AiRun::getDelFlag, 0));
        if (run == null) throw ExecutionException.of(ExecutionErrorCode.RUN_NOT_FOUND_OR_FORBIDDEN, "Run was not found");
        AiRunIntervention intervention = interventionMapper.selectOne(new QueryWrapper<AiRunIntervention>().lambda()
                .eq(AiRunIntervention::getRunId, runId).eq(AiRunIntervention::getTenantId, context.tenantId())
                .eq(AiRunIntervention::getStatus, InterventionStatus.OPEN.name()));
        return new RunCollaborationState(RunStatus.valueOf(run.getStatus()), intervention(intervention));
    }

    @Override
    public NotificationSnapshot build(BusinessNotificationEvent event) {
        AiRun run = runMapper.selectOne(new QueryWrapper<AiRun>().lambda().eq(AiRun::getId, event.runId())
                .eq(AiRun::getTenantId, event.tenantId()).eq(AiRun::getDelFlag, 0));
        if (run == null) throw ExecutionException.of(ExecutionErrorCode.RUN_NOT_FOUND_OR_FORBIDDEN, "Run was not found");
        PipelineDefinition definition = codec.readDefinition(run.getDefinitionJson());
        AiRunIntervention intervention = event.interventionId() == null ? null
                : interventionMapper.selectOne(new QueryWrapper<AiRunIntervention>().lambda()
                .eq(AiRunIntervention::getId, event.interventionId()).eq(AiRunIntervention::getTenantId, event.tenantId())
                .eq("USER_INPUT_REQUIRED".equals(event.eventType()), AiRunIntervention::getStatus, InterventionStatus.OPEN.name()));
        InterventionView interventionView = intervention(intervention);
        return new NotificationSnapshot(run.getTenantId(), run.getId(), RunSource.valueOf(run.getSource()),
                run.getSourceBotId(), run.getSourceChatId(), run.getSourceThreadId(),
                definition.getPipeline().getNotificationBotId(), definition.getPipeline().getDefaultFeishuChatId(),
                summary(run), interventionView);
    }

    private RunSummary summary(AiRun run) {
        List<AiNodeRun> nodes = nodeMapper.selectList(new QueryWrapper<AiNodeRun>().lambda()
                .eq(AiNodeRun::getRunId, run.getId()).eq(AiNodeRun::getTenantId, run.getTenantId()));
        List<StageSummary> stages = nodes.stream().map(node -> new StageSummary(node.getNodeId(), node.getStageCode(),
                NodeStatus.valueOf(node.getStatus()), node.getResultSummary())).toList();
        List<ArtifactView> artifacts = artifactMapper.selectList(new QueryWrapper<AiArtifact>().lambda()
                .eq(AiArtifact::getRunId, run.getId()).eq(AiArtifact::getTenantId, run.getTenantId())
                .orderByAsc(AiArtifact::getCreateTime)).stream().map(this::artifact).toList();
        AiNodeRun end = nodes.stream().filter(node -> "END".equals(node.getNodeType())
                && NodeStatus.SUCCESS.name().equals(node.getStatus())).findFirst().orElse(null);
        return new RunSummary(run.getId(), RunStatus.valueOf(run.getStatus()), stages, artifacts,
                end == null ? null : read(end.getOutputJson()), end == null ? null : end.getResultSummary(),
                !Set.of("SUCCESS", "FAILED", "CANCELED").contains(run.getStatus()));
    }

    private InterventionView intervention(AiRunIntervention value) {
        if (value == null) return null;
        List<InterventionAction> actions;
        try { actions = mapper.readValue(value.getAllowedActionsJson(),
                mapper.getTypeFactory().constructCollectionType(List.class, InterventionAction.class)); }
        catch (Exception ignored) { actions = List.of(); }
        return new InterventionView(value.getId(), value.getNodeRunId(), InterventionType.valueOf(value.getInterventionType()),
                value.getPrompt(), actions, value.getResumeToken(), value.getCreateTime());
    }

    private ArtifactView artifact(AiArtifact value) {
        return new ArtifactView(value.getId(), value.getNodeRunId(), value.getArtifactType(), value.getName(),
                value.getUri(), read(value.getContentJson()), value.getChecksum(), value.getArtifactVersion(),
                read(value.getMetadataJson()), value.getSizeBytes(), value.getCreateTime());
    }

    private JsonNode read(String json) { try { return json == null ? null : mapper.readTree(json); } catch (Exception ignored) { return null; } }
}
