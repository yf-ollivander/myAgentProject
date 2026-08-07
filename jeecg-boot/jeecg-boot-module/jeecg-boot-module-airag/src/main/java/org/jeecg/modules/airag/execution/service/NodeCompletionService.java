package org.jeecg.modules.airag.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.handler.NodeOutcome;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NodeCompletionService {
    private static final int[] RETRY_DELAYS={10,30,60,120,300};
    private final AiRunMapper runMapper;private final AiNodeRunMapper nodeMapper;private final AiArtifactMapper artifactMapper;
    private final AiRunInterventionMapper interventionMapper;private final PipelineDefinitionCodec codec;
    private final ExecutionOutboxService outbox;private final RunEventService events;private final RunDependencyAvailabilityService dependencies;
    private final ObjectMapper mapper;
    public NodeCompletionService(AiRunMapper runMapper,AiNodeRunMapper nodeMapper,AiArtifactMapper artifactMapper,
            AiRunInterventionMapper interventionMapper,PipelineDefinitionCodec codec,ExecutionOutboxService outbox,
            RunEventService events,RunDependencyAvailabilityService dependencies,ObjectMapper mapper){this.runMapper=runMapper;this.nodeMapper=nodeMapper;
        this.artifactMapper=artifactMapper;this.interventionMapper=interventionMapper;this.codec=codec;this.outbox=outbox;this.events=events;this.dependencies=dependencies;this.mapper=mapper;}

    @Transactional(rollbackFor=Exception.class)
    public void complete(NodeClaimService.ClaimedNode claim,NodeOutcome outcome){
        AiRun run=runMapper.selectByIdForUpdate(claim.runId(),claim.tenantId());if(run==null)return;
        List<AiNodeRun> nodes=nodeMapper.selectRunNodesForUpdate(run.getId(),run.getTenantId());
        AiNodeRun node=nodes.stream().filter(n->n.getId().equals(claim.nodeRunId())).findFirst().orElse(null);if(node==null)return;
        // A canceled or recovered lease owns the result boundary; late external results must never advance state.
        if(!NodeStatus.RUNNING.name().equals(node.getStatus())||!Objects.equals(claim.owner(),node.getLeaseOwner())||RunStatus.CANCELED.name().equals(run.getStatus())){
            events.append(run,node.getId(),"NODE_RESULT_DISCARDED",node.getStatus(),node.getStatus(),"Late node result discarded",null,claim.traceId(),null);return;}
        if(!dependencies.available(run)){dependencies.cancelUnavailableLocked(run,"DEPENDENCY_DISABLED");return;}
        PipelineDefinition definition=codec.readDefinition(run.getDefinitionJson());PipelineNode definitionNode=definition.getNodes().stream().filter(n->n.getId().equals(node.getNodeId())).findFirst().orElseThrow();
        switch(outcome.status()){
            case SUCCESS->success(run,node,nodes,definition,definitionNode,outcome,claim.traceId());
            case NEEDS_INPUT->waitForInput(run,node,definition,outcome,claim.traceId());
            case FAILED->failed(run,node,nodes,definition,definitionNode,outcome,claim.traceId());
        }
    }

    private void success(AiRun run,AiNodeRun node,List<AiNodeRun> nodes,PipelineDefinition definition,PipelineNode definitionNode,NodeOutcome outcome,String trace){
        node.setStatus(NodeStatus.SUCCESS.name());node.setOutputJson(write(outcome.output()));node.setResultSummary(abbreviate(outcome.summary()));
        node.setSelectedBranch(outcome.selectedBranch());node.setErrorCode(null);node.setErrorMessage(null);node.setLeaseOwner(null);node.setLeaseUntil(null);node.setEndedAt(new Date());nodeMapper.updateById(node);
        persistArtifacts(run,node,outcome.artifacts());events.append(run,node.getId(),"NODE_SUCCEEDED",NodeStatus.RUNNING.name(),NodeStatus.SUCCESS.name(),outcome.summary(),null,trace,null);
        if(definitionNode.getType()==PipelineEnums.NodeType.AGENT)outbox.businessNotification(run,node,"STAGE_COMPLETED",trace,null);
        if(outcome.notificationMessage()!=null)outbox.notification(run,node,trace,outcome.notificationMessage());
        propagate(run,node,nodes,definition,outcome.selectedBranch(),trace);recalculate(run,nodes,definition,trace);
    }

    private void waitForInput(AiRun run,AiNodeRun node,PipelineDefinition definition,NodeOutcome outcome,String trace){
        node.setStatus(NodeStatus.WAITING.name());node.setLeaseOwner(null);node.setLeaseUntil(null);node.setResultSummary(abbreviate(outcome.summary()));nodeMapper.updateById(node);
        AiRunIntervention intervention=createIntervention(run,node,definition,InterventionType.NEEDS_INPUT,outcome.userPrompt(),Set.of(InterventionAction.SUPPLY_INPUT,InterventionAction.CANCEL));
        transitionRun(run,RunStatus.WAITING,"RUN_WAITING",trace);events.append(run,node.getId(),"NODE_WAITING",NodeStatus.RUNNING.name(),NodeStatus.WAITING.name(),outcome.userPrompt(),null,trace,null);
        outbox.businessNotification(run,node,"USER_INPUT_REQUIRED",trace,intervention.getId());
    }

    private void failed(AiRun run,AiNodeRun node,List<AiNodeRun> nodes,PipelineDefinition definition,PipelineNode definitionNode,NodeOutcome outcome,String trace){
        AgentNodeConfig config=definitionNode.getType()==PipelineEnums.NodeType.AGENT?codec.parseNodeConfig(definitionNode,AgentNodeConfig.class):null;
        int maxRetry=config!=null&&config.getAgentSnapshot()!=null&&config.getAgentSnapshot().getMaxRetry()!=null?config.getAgentSnapshot().getMaxRetry():0;
        if(outcome.retryable()&&node.getRetryCount()<maxRetry){int retry=node.getRetryCount()+1;node.setStatus(NodeStatus.PENDING.name());node.setRetryCount(retry);node.setAttemptNo(node.getAttemptNo()+1);node.setDispatchVersion(node.getDispatchVersion()+1);node.setLeaseOwner(null);node.setLeaseUntil(null);
            node.setErrorCode(outcome.errorCode());node.setErrorMessage(abbreviate(outcome.errorMessage()));node.setNextRetryAt(new Date(System.currentTimeMillis()+1000L*RETRY_DELAYS[Math.min(retry-1,RETRY_DELAYS.length-1)]));nodeMapper.updateById(node);
            outbox.nodeReady(run,node,trace,node.getNextRetryAt());events.append(run,node.getId(),"NODE_RETRY_SCHEDULED",NodeStatus.RUNNING.name(),NodeStatus.PENDING.name(),outcome.errorMessage(),outcome.errorCode(),trace,null);return;}
        PipelineEnums.ErrorPolicy policy=config==null?PipelineEnums.ErrorPolicy.FAIL:config.getOnError();if(policy==PipelineEnums.ErrorPolicy.INHERIT)policy=definition.getPipeline().getInterventionPolicy().getOnRetriesExhausted();
        if(policy==PipelineEnums.ErrorPolicy.WAIT){node.setStatus(NodeStatus.WAITING.name());node.setLeaseOwner(null);node.setLeaseUntil(null);node.setErrorCode(outcome.errorCode());node.setErrorMessage(abbreviate(outcome.errorMessage()));nodeMapper.updateById(node);
            AiRunIntervention intervention=createIntervention(run,node,definition,InterventionType.RETRIES_EXHAUSTED,outcome.errorMessage(),Set.of(InterventionAction.RETRY,InterventionAction.CANCEL));transitionRun(run,RunStatus.WAITING,"RUN_WAITING",trace);outbox.businessNotification(run,node,"USER_INPUT_REQUIRED",trace,intervention.getId());return;}
        node.setStatus(NodeStatus.FAILED.name());node.setLeaseOwner(null);node.setLeaseUntil(null);node.setErrorCode(outcome.errorCode());node.setErrorMessage(abbreviate(outcome.errorMessage()));node.setEndedAt(new Date());nodeMapper.updateById(node);
        events.append(run,node.getId(),"NODE_FAILED",NodeStatus.RUNNING.name(),NodeStatus.FAILED.name(),outcome.errorMessage(),outcome.errorCode(),trace,null);recalculate(run,nodes,definition,trace);
    }

    private void propagate(AiRun run,AiNodeRun completed,List<AiNodeRun> nodes,PipelineDefinition definition,String branch,String trace){
        Map<String,AiNodeRun> byNode=nodes.stream().collect(Collectors.toMap(AiNodeRun::getNodeId,n->n));record Decision(String nodeId,String branch,boolean skipped){}Deque<Decision> queue=new ArrayDeque<>();queue.add(new Decision(completed.getNodeId(),branch,false));int processed=0;
        while(!queue.isEmpty()){if(++processed>100)throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST,"DAG propagation exceeds 100 nodes");Decision decision=queue.removeFirst();
            for(PipelineEdge edge:definition.getEdges().stream().filter(e->e.getSource().equals(decision.nodeId())).toList()){
                AiNodeRun target=byNode.get(edge.getTarget());boolean selected=!decision.skipped()&&decision.branch()!=null&&edge.getBranch().name().equals(decision.branch());
                target.setResolvedIncomingCount(target.getResolvedIncomingCount()+1);if(selected)target.setSelectedIncomingCount(target.getSelectedIncomingCount()+1);
                if(target.getResolvedIncomingCount()>target.getIncomingCount())throw ExecutionException.of(ExecutionErrorCode.RUN_INVALID_REQUEST,"Incoming edge was resolved twice");
                if(target.getResolvedIncomingCount().equals(target.getIncomingCount())){if(target.getSelectedIncomingCount()>0){nodeMapper.updateById(target);outbox.nodeReady(run,target,trace,new Date());events.append(run,target.getId(),"NODE_READY",null,NodeStatus.PENDING.name(),"Node is ready",null,trace,null);}
                    else{target.setStatus(NodeStatus.SKIPPED.name());target.setEndedAt(new Date());nodeMapper.updateById(target);events.append(run,target.getId(),"NODE_SKIPPED",NodeStatus.PENDING.name(),NodeStatus.SKIPPED.name(),"Node path was not selected",null,trace,null);queue.addLast(new Decision(target.getNodeId(),null,true));}}
                else nodeMapper.updateById(target);
            }}
    }

    private void recalculate(AiRun run,List<AiNodeRun> nodes,PipelineDefinition definition,String trace){
        RunStatus next;if(nodes.stream().anyMatch(n->NodeStatus.FAILED.name().equals(n.getStatus())))next=RunStatus.FAILED;
        else if(nodes.stream().anyMatch(n->Set.of(NodeStatus.RUNNING.name(),NodeStatus.PENDING.name()).contains(n.getStatus())))next=RunStatus.RUNNING;
        else if(nodes.stream().anyMatch(n->NodeStatus.WAITING.name().equals(n.getStatus())))next=RunStatus.WAITING;
        else{Set<String> ends=definition.getNodes().stream().filter(n->n.getType()==PipelineEnums.NodeType.END).map(PipelineNode::getId).collect(Collectors.toSet());
            next=nodes.stream().anyMatch(n->ends.contains(n.getNodeId())&&NodeStatus.SUCCESS.name().equals(n.getStatus()))?RunStatus.SUCCESS:RunStatus.FAILED;}
        if(!next.name().equals(run.getStatus()))transitionRun(run,next,"RUN_"+next.name(),trace);
    }

    private void transitionRun(AiRun run,RunStatus next,String event,String trace){String from=run.getStatus();run.setStatus(next.name());if(next==RunStatus.SUCCESS||next==RunStatus.FAILED||next==RunStatus.CANCELED)run.setEndedAt(new Date());runMapper.updateById(run);events.append(run,null,event,from,next.name(),"Run status changed",null,trace,null);if(next==RunStatus.SUCCESS)outbox.businessNotification(run,null,"RUN_SUCCEEDED",trace,null);else if(next==RunStatus.FAILED)outbox.businessNotification(run,null,"RUN_FAILED",trace,null);}
    private AiRunIntervention createIntervention(AiRun run,AiNodeRun node,PipelineDefinition definition,InterventionType type,String prompt,Set<InterventionAction> supported){Set<InterventionAction> allowed=definition.getPipeline().getInterventionPolicy().getAllowedActions().stream().map(action->InterventionAction.valueOf(action.name())).collect(Collectors.toCollection(LinkedHashSet::new));allowed.retainAll(supported);if(allowed.isEmpty())throw ExecutionException.of(ExecutionErrorCode.INTERVENTION_ACTION_INVALID,"No intervention action is allowed");
        AiRunIntervention intervention=new AiRunIntervention();intervention.setTenantId(run.getTenantId());intervention.setRunId(run.getId());intervention.setNodeRunId(node.getId());intervention.setInterventionType(type.name());intervention.setStatus(InterventionStatus.OPEN.name());intervention.setPrompt(abbreviate(prompt));intervention.setAllowedActionsJson(write(allowed));intervention.setResumeToken(UUID.randomUUID().toString());intervention.setCreateTime(new Date());interventionMapper.insert(intervention);return intervention;}
    private void persistArtifacts(AiRun run,AiNodeRun node,List<ArtifactDescriptor> artifacts){if(artifacts==null)return;for(ArtifactDescriptor d:artifacts){AiArtifact a=new AiArtifact();a.setTenantId(run.getTenantId());a.setRunId(run.getId());a.setNodeRunId(node.getId());a.setArtifactType(d.getType().name());a.setName(d.getName());a.setUri(d.getUri());a.setContentJson(d.getContent()==null?null:write(d.getContent()));a.setChecksum(d.getChecksum());a.setArtifactVersion(d.getVersion());a.setMetadataJson(write(d.getMetadata()));a.setSizeBytes((long)(a.getContentJson()==null?0:a.getContentJson().getBytes(StandardCharsets.UTF_8).length));a.setCreateTime(new Date());artifactMapper.insert(a);}}
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String abbreviate(String value){return value==null?null:value.length()>1000?value.substring(0,1000):value;}
}
