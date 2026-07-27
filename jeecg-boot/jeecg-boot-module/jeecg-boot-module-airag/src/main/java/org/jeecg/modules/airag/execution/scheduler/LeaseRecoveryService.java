package org.jeecg.modules.airag.execution.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.service.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Component
public class LeaseRecoveryService {
    private final AiNodeRunMapper nodeMapper;private final AiRunMapper runMapper;private final AiExecutorProperties properties;
    private final ExecutionOutboxService outbox;private final RunEventService events;private final TransactionTemplate transactions;
    private final ExecutionTenantScope tenantScope;
    public LeaseRecoveryService(AiNodeRunMapper nodeMapper,AiRunMapper runMapper,AiExecutorProperties properties,
            ExecutionOutboxService outbox,RunEventService events,TransactionTemplate transactions,ExecutionTenantScope tenantScope){this.nodeMapper=nodeMapper;this.runMapper=runMapper;this.properties=properties;this.outbox=outbox;this.events=events;this.transactions=transactions;this.tenantScope=tenantScope;}
    @Scheduled(fixedDelayString="#{${ai.executor.lease-scan-seconds:30} * 1000}")
    public void recover(){if(!properties.isEnabled())return;Date now=new Date();nodeMapper.selectList(new QueryWrapper<AiNodeRun>().lambda().eq(AiNodeRun::getStatus,NodeStatus.RUNNING.name()).lt(AiNodeRun::getLeaseUntil,now).last("LIMIT 100")).forEach(candidate->tenantScope.run(candidate.getTenantId(),()->transactions.executeWithoutResult(s->recoverOne(candidate,now))));}
    private void recoverOne(AiNodeRun candidate,Date now){AiRun run=runMapper.selectByIdForUpdate(candidate.getRunId(),candidate.getTenantId());if(run==null||Set.of("SUCCESS","FAILED","CANCELED").contains(run.getStatus()))return;AiNodeRun node=nodeMapper.selectByIdForUpdate(candidate.getId(),candidate.getTenantId());if(node==null||!NodeStatus.RUNNING.name().equals(node.getStatus())||node.getLeaseUntil()==null||!node.getLeaseUntil().before(now))return;String trace=UUID.randomUUID().toString();
        if(node.getDispatchCount()>properties.getMaxDispatchRecoveries()){node.setStatus(NodeStatus.FAILED.name());node.setErrorCode(ExecutionErrorCode.EXECUTOR_RECOVERY_EXHAUSTED.name());node.setErrorMessage("Executor recovery limit exceeded");node.setEndedAt(now);nodeMapper.updateById(node);run.setStatus(RunStatus.FAILED.name());run.setEndedAt(now);runMapper.updateById(run);events.append(run,node.getId(),"NODE_FAILED",NodeStatus.RUNNING.name(),NodeStatus.FAILED.name(),node.getErrorMessage(),node.getErrorCode(),trace,null);return;}
        // A new dispatchVersion makes any delayed message from the expired lease harmless.
        node.setStatus(NodeStatus.PENDING.name());node.setDispatchVersion(node.getDispatchVersion()+1);node.setLeaseOwner(null);node.setLeaseUntil(null);node.setNextRetryAt(now);nodeMapper.updateById(node);outbox.nodeReady(run,node,trace,now);events.append(run,node.getId(),"NODE_LEASE_RECOVERED",NodeStatus.RUNNING.name(),NodeStatus.PENDING.name(),"Expired node lease recovered",null,trace,null);}
}
