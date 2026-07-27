package org.jeecg.modules.airag.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.modules.airag.agent.entity.*;
import org.jeecg.modules.airag.agent.mapper.*;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.pipeline.entity.AiPipeline;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineMapper;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RunDependencyAvailabilityService {
    private final AiRunDependencyMapper dependencyMapper; private final AiPipelineMapper pipelineMapper;
    private final AiAgentMapper agentMapper; private final AiConnectorMapper connectorMapper; private final AiFeishuBotMapper botMapper;
    private final AiRunMapper runMapper; private final AiNodeRunMapper nodeMapper; private final AiRunInterventionMapper interventionMapper;
    private final RunEventService events;
    public RunDependencyAvailabilityService(AiRunDependencyMapper dependencyMapper,AiPipelineMapper pipelineMapper,
            AiAgentMapper agentMapper,AiConnectorMapper connectorMapper,AiFeishuBotMapper botMapper,AiRunMapper runMapper,
            AiNodeRunMapper nodeMapper,AiRunInterventionMapper interventionMapper,RunEventService events){this.dependencyMapper=dependencyMapper;
        this.pipelineMapper=pipelineMapper;this.agentMapper=agentMapper;this.connectorMapper=connectorMapper;this.botMapper=botMapper;
        this.runMapper=runMapper;this.nodeMapper=nodeMapper;this.interventionMapper=interventionMapper;this.events=events;}

    public boolean available(AiRun run){
        List<AiRunDependency> deps=dependencyMapper.selectList(new QueryWrapper<AiRunDependency>().lambda()
                .eq(AiRunDependency::getRunId,run.getId()).eq(AiRunDependency::getTenantId,run.getTenantId()));
        for(AiRunDependency d:deps)if(!enabled(d,run.getTenantId()))return false;return true;
    }
    public String firstUnavailable(AiRun run){return dependencyMapper.selectList(new QueryWrapper<AiRunDependency>().lambda()
            .eq(AiRunDependency::getRunId,run.getId()).eq(AiRunDependency::getTenantId,run.getTenantId())).stream()
            .filter(d->!enabled(d,run.getTenantId())).map(d->d.getDependencyType()+":"+d.getDependencyId()).findFirst().orElse(null);}
    private boolean enabled(AiRunDependency d,String tenant){return switch(DependencyType.valueOf(d.getDependencyType())){
        case PIPELINE->{AiPipeline v=pipelineMapper.selectById(d.getDependencyId());yield v!=null&&sameTenant(tenant,v.getTenantId())&&Boolean.TRUE.equals(v.getEnabled())&&Objects.equals(v.getDelFlag(),0);}
        case AGENT->{AiAgent v=agentMapper.selectById(d.getDependencyId());yield v!=null&&sameTenant(tenant,v.getTenantId())&&Boolean.TRUE.equals(v.getEnabled())&&Objects.equals(v.getDelFlag(),0);}
        case CONNECTOR->{AiConnector v=connectorMapper.selectById(d.getDependencyId());yield v!=null&&sameTenant(tenant,v.getTenantId())&&Boolean.TRUE.equals(v.getEnabled())&&Objects.equals(v.getDelFlag(),0);}
        case BOT->{AiFeishuBot v=botMapper.selectById(d.getDependencyId());yield v!=null&&sameTenant(tenant,v.getTenantId())&&Boolean.TRUE.equals(v.getEnabled())&&Objects.equals(v.getDelFlag(),0);}};}
    private boolean sameTenant(String expected,String actual){return Objects.equals(normalizeTenant(expected),normalizeTenant(actual));}
    private String normalizeTenant(String tenant){return tenant==null||tenant.isBlank()?"0":tenant;}

    public void cancelUnavailableLocked(AiRun run,String prefix){String unavailable=firstUnavailable(run);if(unavailable==null)return;
        String reason=prefix+":"+unavailable;String from=run.getStatus();run.setStatus(RunStatus.CANCELED.name());run.setCancelReason(reason);run.setEndedAt(new Date());runMapper.updateById(run);
        nodeMapper.selectRunNodesForUpdate(run.getId(),run.getTenantId()).stream().filter(n->Set.of("PENDING","RUNNING","WAITING").contains(n.getStatus())).forEach(n->{n.setStatus(NodeStatus.CANCELED.name());n.setEndedAt(new Date());nodeMapper.updateById(n);});
        interventionMapper.selectList(new QueryWrapper<AiRunIntervention>().lambda().eq(AiRunIntervention::getRunId,run.getId()).eq(AiRunIntervention::getTenantId,run.getTenantId()).eq(AiRunIntervention::getStatus,InterventionStatus.OPEN.name())).forEach(i->{i.setStatus(InterventionStatus.CANCELED.name());i.setResolvedAt(new Date());interventionMapper.updateById(i);});
        events.append(run,null,"RUN_CANCELED",from,RunStatus.CANCELED.name(),reason,null,UUID.randomUUID().toString(),null);}
}
