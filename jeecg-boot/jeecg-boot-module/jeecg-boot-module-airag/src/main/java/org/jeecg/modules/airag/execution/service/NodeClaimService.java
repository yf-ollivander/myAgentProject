package org.jeecg.modules.airag.execution.service;

import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;

@Service
public class NodeClaimService {
    private final AiRunMapper runMapper; private final AiNodeRunMapper nodeMapper; private final RunEventService events;
    private final RunDependencyAvailabilityService dependencies;
    private final ExecutionOutboxService outbox;
    public NodeClaimService(AiRunMapper runMapper,AiNodeRunMapper nodeMapper,RunEventService events,
                            RunDependencyAvailabilityService dependencies,ExecutionOutboxService outbox){this.runMapper=runMapper;this.nodeMapper=nodeMapper;this.events=events;this.dependencies=dependencies;this.outbox=outbox;}

    @Transactional(rollbackFor=Exception.class)
    public ClaimedNode claim(String tenantId,String runId,String nodeRunId,long dispatchVersion,String owner,String traceId,Date leaseUntil){
        AiRun run=runMapper.selectByIdForUpdate(runId,tenantId); if(run==null)return null;
        RunStatus runStatus=RunStatus.valueOf(run.getStatus()); if(new RunStateMachine().terminal(runStatus))return null;
        if(!dependencies.available(run)){ dependencies.cancelUnavailableLocked(run,"DEPENDENCY_DISABLED"); return null; }
        if(nodeMapper.claimPending(nodeRunId,tenantId,dispatchVersion,owner,leaseUntil)!=1)return null;
        AiNodeRun node=nodeMapper.selectByIdForUpdate(nodeRunId,tenantId);
        if(runStatus==RunStatus.CREATED||runStatus==RunStatus.WAITING){run.setStatus(RunStatus.RUNNING.name());if(run.getStartedAt()==null)run.setStartedAt(new Date());runMapper.updateById(run);
            events.append(run,nodeRunId,"RUN_STARTED",runStatus.name(),RunStatus.RUNNING.name(),"Run execution started",null,traceId,null);
            if(runStatus==RunStatus.CREATED)outbox.businessNotification(run,node,"RUN_STARTED",traceId,null);}
        events.append(run,nodeRunId,"NODE_STARTED",NodeStatus.PENDING.name(),NodeStatus.RUNNING.name(),"Node started",null,traceId,null);
        return new ClaimedNode(tenantId,runId,nodeRunId,node.getNodeId(),owner,traceId);
    }
    public record ClaimedNode(String tenantId,String runId,String nodeRunId,String nodeId,String owner,String traceId){}
}
