package org.jeecg.modules.airag.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.*;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;

@Service
public class RunOperationService implements RunInterventionService {
    private final AiRunMapper runMapper;private final AiNodeRunMapper nodeMapper;private final AiRunInterventionMapper interventionMapper;
    private final ExecutionOutboxService outbox;private final RunEventService events;private final RunCreationService creation;private final ObjectMapper mapper;
    private final RunDependencyAvailabilityService dependencies;
    public RunOperationService(AiRunMapper runMapper,AiNodeRunMapper nodeMapper,AiRunInterventionMapper interventionMapper,
            ExecutionOutboxService outbox,RunEventService events,RunCreationService creation,ObjectMapper mapper,RunDependencyAvailabilityService dependencies){
        this.runMapper=runMapper;this.nodeMapper=nodeMapper;this.interventionMapper=interventionMapper;this.outbox=outbox;this.events=events;this.creation=creation;this.mapper=mapper;this.dependencies=dependencies;}

    @Transactional(rollbackFor=Exception.class)
    public void cancel(String runId,RunCancelRequest request,AgentAccessContext context){AiRun run=requireLocked(runId,context);cancelLocked(run,request.getRequestId(),request.getReason(),UUID.randomUUID().toString());}

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void resolve(String runId,String interventionId,InterventionResolveRequest request,AgentAccessContext context){
        AiRun run=requireLocked(runId,context);List<AiNodeRun> nodes=nodeMapper.selectRunNodesForUpdate(runId,context.tenantId());
        String resumeJson=write(request.getResumeInput());
        AiRunIntervention byRequest=interventionMapper.selectByResolveRequestIdForUpdate(context.tenantId(),request.getRequestId());
        if(byRequest!=null){requireSameResolution(byRequest,runId,interventionId,request,resumeJson);return;}
        if(StringUtils.hasText(request.getSourceMessageId())){AiRunIntervention byMessage=interventionMapper.selectBySourceMessageIdForUpdate(context.tenantId(),request.getSourceMessageId());if(byMessage!=null){requireSameResolution(byMessage,runId,interventionId,request,resumeJson);return;}}
        AiRunIntervention intervention=interventionMapper.selectByIdForUpdate(interventionId,context.tenantId());
        if(intervention==null||!runId.equals(intervention.getRunId()))throw ExecutionException.of(ExecutionErrorCode.INTERVENTION_NOT_FOUND_OR_CLOSED,"Intervention was not found");
        if(!InterventionStatus.OPEN.name().equals(intervention.getStatus()))throw ExecutionException.of(ExecutionErrorCode.INTERVENTION_NOT_FOUND_OR_CLOSED,"Intervention is already closed");
        List<InterventionAction> allowed=readActions(intervention.getAllowedActionsJson());if(!allowed.contains(request.getAction()))throw ExecutionException.of(ExecutionErrorCode.INTERVENTION_ACTION_INVALID,"Intervention action is not allowed");
        if(request.getAction()==InterventionAction.CANCEL){markResolved(intervention,request,resumeJson,context.username());cancelLocked(run,request.getRequestId(),"Canceled by intervention",UUID.randomUUID().toString());return;}
        AiNodeRun node=nodes.stream().filter(n->n.getId().equals(intervention.getNodeRunId())).findFirst().orElseThrow();if(!NodeStatus.WAITING.name().equals(node.getStatus()))throw ExecutionException.of(ExecutionErrorCode.INTERVENTION_NOT_FOUND_OR_CLOSED,"Waiting node was not found");
        markResolved(intervention,request,resumeJson,context.username());
        node.setStatus(NodeStatus.PENDING.name());node.setDispatchVersion(node.getDispatchVersion()+1);node.setLeaseOwner(null);node.setLeaseUntil(null);node.setNextRetryAt(new Date());node.setErrorCode(null);node.setErrorMessage(null);
        if(request.getAction()==InterventionAction.SUPPLY_INPUT){node.setInputJson(resumeJson);node.setResumeGeneration(node.getResumeGeneration()+1);}else{node.setManualRetryCount(node.getManualRetryCount()+1);node.setAttemptNo(node.getAttemptNo()+1);}
        nodeMapper.updateById(node);String from=run.getStatus();run.setStatus(RunStatus.RUNNING.name());runMapper.updateById(run);String trace=UUID.randomUUID().toString();outbox.nodeReady(run,node,trace,new Date());events.append(run,node.getId(),"NODE_RESUMED",NodeStatus.WAITING.name(),NodeStatus.PENDING.name(),"Node resumed",null,trace,null);events.append(run,null,"RUN_RESUMED",from,RunStatus.RUNNING.name(),"Run resumed",null,trace,null);outbox.businessNotification(run,node,"RUN_RESUMED",trace,intervention.getId());
    }

    @Transactional(rollbackFor=Exception.class)
    public RunCreateResult retry(String runId,RunRetryRequest request,AgentAccessContext context){AiRun run=requireLocked(runId,context);if(!dependencies.available(run))throw ExecutionException.of(ExecutionErrorCode.RUN_DEPENDENCY_UNAVAILABLE,"Run dependency is unavailable");return creation.retryFailed(run,request.getRequestId(),context);}

    private void cancelLocked(AiRun run,String requestId,String reason,String trace){if(RunStatus.CANCELED.name().equals(run.getStatus())){if(Objects.equals(run.getCancelRequestId(),requestId))return;throw ExecutionException.of(ExecutionErrorCode.RUN_ALREADY_TERMINAL,"Run is already canceled");}
        if(Set.of(RunStatus.SUCCESS.name(),RunStatus.FAILED.name()).contains(run.getStatus()))throw ExecutionException.of(ExecutionErrorCode.RUN_ALREADY_TERMINAL,"Terminal run cannot be canceled");String from=run.getStatus();run.setStatus(RunStatus.CANCELED.name());run.setCancelRequestId(requestId);run.setCancelReason(reason);run.setEndedAt(new Date());runMapper.updateById(run);
        nodeMapper.selectRunNodesForUpdate(run.getId(),run.getTenantId()).stream().filter(n->Set.of(NodeStatus.PENDING.name(),NodeStatus.RUNNING.name(),NodeStatus.WAITING.name()).contains(n.getStatus())).forEach(n->{n.setStatus(NodeStatus.CANCELED.name());n.setEndedAt(new Date());nodeMapper.updateById(n);});
        interventionMapper.selectList(new QueryWrapper<AiRunIntervention>().lambda().eq(AiRunIntervention::getRunId,run.getId()).eq(AiRunIntervention::getTenantId,run.getTenantId()).eq(AiRunIntervention::getStatus,InterventionStatus.OPEN.name())).forEach(i->{i.setStatus(InterventionStatus.CANCELED.name());i.setResolvedAt(new Date());interventionMapper.updateById(i);});events.append(run,null,"RUN_CANCELED",from,RunStatus.CANCELED.name(),reason,null,trace,null);outbox.businessNotification(run,null,"RUN_CANCELED",trace,null);}
    private AiRun requireLocked(String id,AgentAccessContext context){AiRun run=runMapper.selectByIdForUpdate(id,context.tenantId());if(run==null)throw ExecutionException.of(ExecutionErrorCode.RUN_NOT_FOUND_OR_FORBIDDEN,"Run was not found or forbidden");return run;}
    private String write(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST,"Request JSON is invalid");}}
    private List<InterventionAction> readActions(String json){try{return mapper.readValue(json,mapper.getTypeFactory().constructCollectionType(List.class,InterventionAction.class));}catch(Exception e){throw new IllegalStateException(e);}}
    private void markResolved(AiRunIntervention intervention,InterventionResolveRequest request,String resumeJson,String username){intervention.setStatus(InterventionStatus.RESOLVED.name());intervention.setResolveRequestId(request.getRequestId());intervention.setSourceMessageId(request.getSourceMessageId());intervention.setResolvedAction(request.getAction().name());intervention.setResumeInputJson(resumeJson);intervention.setResolvedBy(username);intervention.setResolvedAt(new Date());interventionMapper.updateById(intervention);}
    private void requireSameResolution(AiRunIntervention existing,String runId,String interventionId,InterventionResolveRequest request,String resumeJson){boolean same=Objects.equals(existing.getRunId(),runId)&&Objects.equals(existing.getId(),interventionId)&&Objects.equals(existing.getResolvedAction(),request.getAction().name())&&Objects.equals(existing.getResumeInputJson(),resumeJson)&&Objects.equals(existing.getSourceMessageId(),request.getSourceMessageId());if(!same)throw ExecutionException.of(ExecutionErrorCode.INTERVENTION_REQUEST_CONFLICT,"Intervention idempotency key belongs to a different request");}
}
