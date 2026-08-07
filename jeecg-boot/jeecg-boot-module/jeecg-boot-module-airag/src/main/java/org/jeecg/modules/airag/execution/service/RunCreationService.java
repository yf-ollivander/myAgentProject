package org.jeecg.modules.airag.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.TextNode;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.service.*;
import org.jeecg.modules.airag.agent.support.AgentConfigSnapshotSanitizer;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.gateway.*;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.service.PublishedPipelineProvider;
import org.jeecg.modules.airag.pipeline.validation.PipelineDefinitionNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class RunCreationService implements RunStartService {
    private final PublishedPipelineProvider pipelineProvider; private final AuthorizedAgentConfigProvider agentProvider;
    private final AiFeishuBotMapper botMapper; private final PipelineDefinitionCodec codec; private final PipelineDefinitionNormalizer normalizer;
    private final AiRunMapper runMapper; private final AiNodeRunMapper nodeMapper; private final AiRunDependencyMapper dependencyMapper;
    private final RunEventService eventService; private final ExecutionOutboxService outboxService; private final ObjectMapper mapper;
    private final AiExecutorProperties properties;
    private final AgentExecutionGateway gateway;
    private final AgentConfigSnapshotSanitizer snapshotSanitizer;

    public RunCreationService(PublishedPipelineProvider pipelineProvider, AuthorizedAgentConfigProvider agentProvider,
            AiFeishuBotMapper botMapper, PipelineDefinitionCodec codec, PipelineDefinitionNormalizer normalizer,
            AiRunMapper runMapper, AiNodeRunMapper nodeMapper, AiRunDependencyMapper dependencyMapper,
            RunEventService eventService, ExecutionOutboxService outboxService, ObjectMapper mapper, AiExecutorProperties properties,
            AgentExecutionGateway gateway, AgentConfigSnapshotSanitizer snapshotSanitizer) {
        this.pipelineProvider=pipelineProvider; this.agentProvider=agentProvider; this.botMapper=botMapper; this.codec=codec;
        this.normalizer=normalizer; this.runMapper=runMapper; this.nodeMapper=nodeMapper; this.dependencyMapper=dependencyMapper;
        this.eventService=eventService; this.outboxService=outboxService; this.mapper=mapper; this.properties=properties;
        this.gateway=gateway;
        this.snapshotSanitizer=snapshotSanitizer;
    }

    @Transactional(rollbackFor=Exception.class)
    public RunCreateResult retryFailed(AiRun original,String requestId,AgentAccessContext context){
        if(!RunStatus.FAILED.name().equals(original.getStatus()))throw ExecutionException.of(ExecutionErrorCode.RUN_NOT_RETRYABLE,"Only FAILED runs can be retried");
        String retryHash=hash("retry|"+original.getId()+"|"+requestId);AiRun existing=findExisting(context.tenantId(),requestId,RunSourceContext.jeecg());if(existing!=null){if(!retryHash.equals(existing.getRequestPayloadHash()))throw ExecutionException.of(ExecutionErrorCode.RUN_REQUEST_CONFLICT,"requestId already belongs to another retry");return new RunCreateResult(existing.getId(),RunStatus.valueOf(existing.getStatus()),true);}
        String traceId=UUID.randomUUID().toString(),runId=UUID.randomUUID().toString();Date now=new Date();AiRun run=new AiRun();
        run.setId(runId);run.setTenantId(context.tenantId());run.setCreateBy(context.username());run.setCreateTime(now);run.setDelFlag(0);run.setRequestId(requestId);
        run.setRequestPayloadHash(retryHash);run.setRunType(original.getRunType());run.setSource(RunSource.JEECG.name());run.setInitiatorUsername(context.username());
        run.setPipelineId(original.getPipelineId());run.setPipelineVersionId(original.getPipelineVersionId());run.setPipelineVersion(original.getPipelineVersion());run.setDefinitionHash(original.getDefinitionHash());
        run.setDefinitionJson(original.getDefinitionJson());run.setInputJson(original.getInputJson());run.setWorkspaceContextJson(original.getWorkspaceContextJson());run.setStatus(RunStatus.CREATED.name());
        run.setRetryOfRunId(original.getId());run.setRootRunId(original.getRootRunId());run.setEventSequence(0L);run.setVersion(0L);
        try{runMapper.insert(run);}catch(DuplicateKeyException duplicate){AiRun winner=findExisting(context.tenantId(),requestId,RunSourceContext.jeecg());if(winner!=null&&retryHash.equals(winner.getRequestPayloadHash()))return new RunCreateResult(winner.getId(),RunStatus.valueOf(winner.getStatus()),true);throw ExecutionException.of(ExecutionErrorCode.RUN_REQUEST_CONFLICT,"Concurrent retry conflicts with an existing run");}
        PipelineDefinition definition=codec.readDefinition(run.getDefinitionJson());Map<String,AiNodeRun> nodes=insertNodes(run,definition);
        dependencyMapper.selectList(new QueryWrapper<AiRunDependency>().lambda().eq(AiRunDependency::getRunId,original.getId()).eq(AiRunDependency::getTenantId,context.tenantId())).forEach(old->{AiRunDependency copy=new AiRunDependency();copy.setTenantId(run.getTenantId());copy.setRunId(run.getId());copy.setDependencyType(old.getDependencyType());copy.setDependencyId(old.getDependencyId());copy.setCreateTime(now);dependencyMapper.insert(copy);});
        eventService.append(run,null,"RUN_CREATED",null,RunStatus.CREATED.name(),"Retry run created",null,traceId,null);
        AiNodeRun start=definition.getNodes().stream().filter(n->n.getType()==PipelineEnums.NodeType.START).findFirst().map(n->nodes.get(n.getId())).orElseThrow();outboxService.nodeReady(run,start,traceId,now);
        return new RunCreateResult(runId,RunStatus.CREATED,false);
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public RunCreateResult startPipeline(RunCreateRequest request, AgentAccessContext context, RunSourceContext source) {
        validateCommon(request, RunType.PIPELINE);
        PublishedPipelineSnapshot snapshot=pipelineProvider.resolveEnabledForRun(request.getPipelineId(),context);
        validateStartInput(snapshot.getDefinition(),request.getInput());
        return create(request,context,source,snapshot.getDefinition(),snapshot.getDefinitionHash(),snapshot,null);
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public RunCreateResult startDirectAgent(RunCreateRequest request, AgentAccessContext context, RunSourceContext source) {
        validateCommon(request,RunType.AGENT_DIRECT);
        AgentConfigSnapshot agent=agentProvider.resolveEnabledSnapshot(request.getAgentId(),context);
        if (agent.getConnector() == null || !"1.1".equals(agent.getConnector().getResultContractVersion())) {
            throw ExecutionException.of(ExecutionErrorCode.RUN_DEPENDENCY_UNAVAILABLE,
                    "Agent Connector must use Result 1.1");
        }
        AiFeishuBot bot=requireNotificationBot(agent,context,source);
        // Direct runs persist their generated definition, so they must use the same secret-free snapshot as publication.
        PipelineDefinition definition=directDefinition(snapshotSanitizer.sanitize(agent),bot);
        PipelineDefinitionNormalizer.NormalizedDefinition normalized=normalizer.normalize(definition);
        return create(request,context,source,normalized.definition(),normalized.hash(),null,agent);
    }

    private RunCreateResult create(RunCreateRequest request, AgentAccessContext context, RunSourceContext source,
            PipelineDefinition definition, String definitionHash, PublishedPipelineSnapshot snapshot, AgentConfigSnapshot directAgent) {
        String requestHash=hash(request.getRunType()+"|"+(request.getPipelineId()==null?request.getAgentId():request.getPipelineId())+"|"+canonical(request.getInput()));
        AiRun existing=findExisting(context.tenantId(),request.getRequestId(),source);
        if(existing!=null){ if(!requestHash.equals(existing.getRequestPayloadHash())) throw ExecutionException.of(
                ExecutionErrorCode.RUN_REQUEST_CONFLICT,"requestId already belongs to a different request");
            return new RunCreateResult(existing.getId(),RunStatus.valueOf(existing.getStatus()),true); }
        String runId=UUID.randomUUID().toString(), traceId=UUID.randomUUID().toString(); Date now=new Date();
        AiRun run=new AiRun(); run.setId(runId); run.setTenantId(context.tenantId()); run.setCreateBy(context.username()); run.setCreateTime(now);
        run.setDelFlag(0); run.setRequestId(request.getRequestId()); run.setRequestPayloadHash(requestHash); run.setRunType(request.getRunType().name());
        run.setSource(source.source().name()); run.setSourceEventId(source.sourceEventId()); run.setSourceBotId(source.botId());
        run.setSourceChatId(source.chatId()); run.setSourceThreadId(source.threadId()); run.setInitiatorUsername(context.username());
        run.setPipelineId(snapshot==null?null:snapshot.getPipelineId()); run.setPipelineVersionId(snapshot==null?null:snapshot.getVersionId());
        run.setPipelineVersion(snapshot==null?null:snapshot.getVersion()); run.setDefinitionHash(definitionHash); run.setDefinitionJson(codec.write(definition));
        run.setInputJson(write(request.getInput())); run.setWorkspaceContextJson("{}"); run.setStatus(RunStatus.CREATED.name());
        run.setRootRunId(runId); run.setEventSequence(0L); run.setVersion(0L);
        try { runMapper.insert(run); } catch (DuplicateKeyException duplicate) {
            // MySQL unique keys serialize concurrent requestId/sourceEvent creation without a distributed lock.
            AiRun winner=findExisting(context.tenantId(),request.getRequestId(),source);
            if(winner!=null&&requestHash.equals(winner.getRequestPayloadHash()))return new RunCreateResult(winner.getId(),RunStatus.valueOf(winner.getStatus()),true);
            throw ExecutionException.of(ExecutionErrorCode.RUN_REQUEST_CONFLICT,"Concurrent request conflicts with an existing run");
        }
        Map<String,AiNodeRun> nodes=insertNodes(run,definition);
        insertDependencies(run,definition,snapshot,directAgent);
        eventService.append(run,null,"RUN_CREATED",null,RunStatus.CREATED.name(),"Run created",null,traceId,null);
        AiNodeRun start=definition.getNodes().stream().filter(n->n.getType()==PipelineEnums.NodeType.START).findFirst()
                .map(n->nodes.get(n.getId())).orElseThrow();
        outboxService.nodeReady(run,start,traceId,now);
        return new RunCreateResult(runId,RunStatus.CREATED,false);
    }

    private Map<String,AiNodeRun> insertNodes(AiRun run,PipelineDefinition definition){
        Map<String,AiNodeRun> result=new LinkedHashMap<>();
        for(PipelineNode node:definition.getNodes()){
            AiNodeRun item=new AiNodeRun(); item.setTenantId(run.getTenantId()); item.setRunId(run.getId()); item.setNodeId(node.getId());
            item.setNodeType(node.getType().name()); item.setStatus(NodeStatus.PENDING.name());
            item.setIncomingCount((int)definition.getEdges().stream().filter(e->node.getId().equals(e.getTarget())).count());
            item.setResolvedIncomingCount(0); item.setSelectedIncomingCount(0); item.setAttemptNo(0); item.setRetryCount(0);
            item.setManualRetryCount(0); item.setResumeGeneration(0); item.setDispatchVersion(0L); item.setDispatchCount(0); item.setVersion(0L);
            if(node.getType()==PipelineEnums.NodeType.AGENT) item.setStageCode(codec.parseNodeConfig(node,AgentNodeConfig.class).getStageCode());
            nodeMapper.insert(item); result.put(node.getId(),item);
        } return result;
    }

    private void insertDependencies(AiRun run,PipelineDefinition definition,PublishedPipelineSnapshot snapshot,AgentConfigSnapshot direct){
        Set<String> keys=new LinkedHashSet<>();
        if(snapshot!=null) dependency(run,DependencyType.PIPELINE,snapshot.getPipelineId(),keys);
        for(PipelineNode node:definition.getNodes()) if(node.getType()==PipelineEnums.NodeType.AGENT){
            AgentNodeConfig config=codec.parseNodeConfig(node,AgentNodeConfig.class); AgentConfigSnapshot agent=config.getAgentSnapshot();
            if(agent==null&&direct!=null)agent=direct; dependency(run,DependencyType.AGENT,config.getAgentId(),keys);
            if(agent!=null&&agent.getConnector()!=null)dependency(run,DependencyType.CONNECTOR,agent.getConnector().getConnectorId(),keys);
        }
        dependency(run,DependencyType.BOT,definition.getPipeline().getNotificationBotId(),keys);
    }

    private void dependency(AiRun run,DependencyType type,String id,Set<String> keys){
        if(!StringUtils.hasText(id)||!keys.add(type+":"+id))return; AiRunDependency dep=new AiRunDependency();
        dep.setTenantId(run.getTenantId()); dep.setRunId(run.getId()); dep.setDependencyType(type.name()); dep.setDependencyId(id);
        dep.setCreateTime(new Date()); dependencyMapper.insert(dep);
    }

    private AiRun findExisting(String tenant,String requestId,RunSourceContext source){
        AiRun byRequest=runMapper.selectOne(new QueryWrapper<AiRun>().lambda().eq(AiRun::getTenantId,tenant)
                .eq(AiRun::getRequestId,requestId).eq(AiRun::getDelFlag,0));
        if(byRequest!=null)return byRequest;
        if(source.source()==RunSource.FEISHU&&StringUtils.hasText(source.sourceEventId())) return runMapper.selectOne(
                new QueryWrapper<AiRun>().lambda().eq(AiRun::getTenantId,tenant).eq(AiRun::getSource,RunSource.FEISHU.name())
                        .eq(AiRun::getSourceEventId,source.sourceEventId()).eq(AiRun::getDelFlag,0));
        return null;
    }

    private void validateCommon(RunCreateRequest request,RunType expected){
        if(!properties.isEnabled()||gateway instanceof DisabledAgentExecutionGateway) throw ExecutionException.of(
                ExecutionErrorCode.EXECUTOR_GATEWAY_UNAVAILABLE,"Executor gateway is unavailable");
        boolean valid=request!=null&&request.getRunType()==expected&&request.getInput()!=null&&
                (expected==RunType.PIPELINE?StringUtils.hasText(request.getPipelineId())&&!StringUtils.hasText(request.getAgentId())
                        :StringUtils.hasText(request.getAgentId())&&!StringUtils.hasText(request.getPipelineId()));
        if(!valid||write(request.getInput()).getBytes(StandardCharsets.UTF_8).length>1024*1024) throw ExecutionException.of(
                ExecutionErrorCode.RUN_INVALID_REQUEST,"Run type, target, or input is invalid");
    }

    private void validateStartInput(PipelineDefinition definition,JsonNode input){
        StartNodeConfig config=definition.getNodes().stream().filter(n->n.getType()==PipelineEnums.NodeType.START).findFirst()
                .map(n->codec.parseNodeConfig(n,StartNodeConfig.class)).orElseThrow();
        for(FieldSchema field:config.getInputSchema()){JsonNode value=input==null?null:input.get(field.effectiveName());if(Boolean.TRUE.equals(field.getRequired())&&value==null)throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST,"Required input is missing: "+field.effectiveName());if(value!=null&&!matchesType(value,field.getType()))throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST,"Input type is invalid: "+field.effectiveName());}
    }

    private AiFeishuBot requireNotificationBot(AgentConfigSnapshot agent,AgentAccessContext context,RunSourceContext source){
        String botId = source.source() == RunSource.FEISHU ? source.botId()
                : agent.getFeishuBot() == null ? null : agent.getFeishuBot().getBotId();
        if(!StringUtils.hasText(botId))throw ExecutionException.of(ExecutionErrorCode.RUN_DEPENDENCY_UNAVAILABLE,"Notification bot is required");
        QueryWrapper<AiFeishuBot> botQuery=new QueryWrapper<>();botQuery.lambda().eq(AiFeishuBot::getId,botId)
                .eq(AiFeishuBot::getEnabled,true).eq(AiFeishuBot::getDelFlag,0)
                .and("0".equals(context.tenantId()),q->q.eq(AiFeishuBot::getTenantId,"0")
                        .or().isNull(AiFeishuBot::getTenantId).or().eq(AiFeishuBot::getTenantId,""))
                .eq(!"0".equals(context.tenantId()),AiFeishuBot::getTenantId,context.tenantId());
        AiFeishuBot bot=botMapper.selectOne(botQuery);
        if(bot==null)throw ExecutionException.of(ExecutionErrorCode.RUN_DEPENDENCY_UNAVAILABLE,"Enabled notification bot is required");
        if(source.source()==RunSource.JEECG){
            if(!"DIRECT_AGENT".equals(bot.getEntryMode())||!StringUtils.hasText(bot.getDefaultChatId()))
                throw ExecutionException.of(ExecutionErrorCode.RUN_DEPENDENCY_UNAVAILABLE,"Enabled DIRECT_AGENT bot and default chat are required");
        }else if("DIRECT_AGENT".equals(bot.getEntryMode())){
            // A direct bot remains bound to exactly one Agent; only an orchestrator may launch an unbound role.
            if(agent.getFeishuBot()==null||!botId.equals(agent.getFeishuBot().getBotId()))
                throw ExecutionException.of(ExecutionErrorCode.RUN_DEPENDENCY_UNAVAILABLE,"Source bot is not bound to the Agent");
        }else if(!"ORCHESTRATOR".equals(bot.getEntryMode())){
            throw ExecutionException.of(ExecutionErrorCode.RUN_DEPENDENCY_UNAVAILABLE,"Unsupported bot entry mode");
        }
        return bot;
    }

    private PipelineDefinition directDefinition(AgentConfigSnapshot agent,AiFeishuBot bot){
        PipelineDefinition definition=new PipelineDefinition(); definition.setSchemaVersion("1.1");
        PipelineDefinition.PipelineMetadata metadata=new PipelineDefinition.PipelineMetadata(); metadata.setCode("direct_agent");
        metadata.setName("Direct Agent"); metadata.setNotificationBotId(bot.getId()); metadata.setDefaultFeishuChatId(bot.getDefaultChatId());
        metadata.setFinalSummaryTemplate("{{nodes.agent.summary}}"); PipelineDefinition.InterventionPolicy policy=new PipelineDefinition.InterventionPolicy();
        policy.setOnAgentNeedsUser(PipelineEnums.ErrorPolicy.WAIT); policy.setOnRetriesExhausted(PipelineEnums.ErrorPolicy.FAIL);
        policy.setAllowedActions(List.of(PipelineEnums.InterventionAction.SUPPLY_INPUT,PipelineEnums.InterventionAction.RETRY,PipelineEnums.InterventionAction.CANCEL));
        metadata.setInterventionPolicy(policy); definition.setPipeline(metadata);
        PipelineNode start=node("start",PipelineEnums.NodeType.START,new StartNodeConfig());
        AgentNodeConfig ac=new AgentNodeConfig(); ac.setStageCode("direct_agent"); ac.setAgentId(agent.getAgentId()); ac.setResultContractVersion("1.1");
        ac.setInput(Map.of()); ac.setOnError(PipelineEnums.ErrorPolicy.INHERIT); ac.setAgentSnapshot(agent);
        PipelineNode agentNode=node("agent",PipelineEnums.NodeType.AGENT,ac); NotifyNodeConfig notify=new NotifyNodeConfig(); notify.setMessageTemplate("{{nodes.agent.summary}}");
        EndNodeConfig end=new EndNodeConfig(); end.setOutput(Map.of("result",TextNode.valueOf("{{nodes.agent.output}}"))); end.setCompletionSummary("{{nodes.agent.summary}}");
        definition.setNodes(List.of(start,agentNode,node("notify",PipelineEnums.NodeType.NOTIFY,notify),node("end",PipelineEnums.NodeType.END,end)));
        definition.setEdges(List.of(edge("e1","start","agent"),edge("e2","agent","notify"),edge("e3","notify","end"))); return definition;
    }
    private PipelineNode node(String id,PipelineEnums.NodeType type,Object config){ PipelineNode n=new PipelineNode();n.setId(id);n.setType(type);n.setName(id);n.setConfig(codec.valueToTree(config));return n; }
    private PipelineEdge edge(String id,String from,String to){ PipelineEdge e=new PipelineEdge();e.setId(id);e.setSource(from);e.setTarget(to);e.setBranch(PipelineEnums.EdgeBranch.DEFAULT);return e; }
    private String canonical(JsonNode node){ if(node==null)return"null"; if(node.isObject()){List<String> names=new ArrayList<>();node.fieldNames().forEachRemaining(names::add);Collections.sort(names);StringBuilder b=new StringBuilder("{");for(String n:names)b.append(n).append(':').append(canonical(node.get(n))).append(';');return b.append('}').toString();} if(node.isArray()){StringBuilder b=new StringBuilder("[");node.forEach(n->b.append(canonical(n)).append(';'));return b.append(']').toString();}return node.toString(); }
    private String hash(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST,"JSON cannot be serialized");}}
    private boolean matchesType(JsonNode value,PipelineEnums.ValueType type){if(type==null)return true;return switch(type){case STRING->value.isTextual();case NUMBER->value.isNumber();case BOOLEAN->value.isBoolean();case OBJECT->value.isObject();case ARRAY->value.isArray();};}
}
