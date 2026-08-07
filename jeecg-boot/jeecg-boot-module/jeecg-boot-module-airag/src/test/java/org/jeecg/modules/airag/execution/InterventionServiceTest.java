package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.InterventionResolveRequest;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterventionServiceTest {
    @Test void repeatedResolveRequestReturnsWithoutExecutingAgain(){Fixture fixture=new Fixture();InterventionResolveRequest request=fixture.request(InterventionAction.CANCEL);fixture.service.resolve("r","i",request,new AgentAccessContext("admin","0"));verifyNoInteractions(fixture.outbox);verify(fixture.interventions,never()).updateById(any(AiRunIntervention.class));}
    @Test void reusedRequestWithDifferentPayloadConflicts(){Fixture fixture=new Fixture();assertThrows(ExecutionException.class,()->fixture.service.resolve("r","i",fixture.request(InterventionAction.RETRY),new AgentAccessContext("admin","0")));}
    @Test void firstCancelResolvesInterventionBeforeCancelingRun(){AiRunMapper runs=mock(AiRunMapper.class);AiNodeRunMapper nodes=mock(AiNodeRunMapper.class);AiRunInterventionMapper interventions=mock(AiRunInterventionMapper.class);AiRun run=new AiRun();run.setId("r");run.setTenantId("0");run.setStatus(RunStatus.RUNNING.name());when(runs.selectByIdForUpdate("r","0")).thenReturn(run);when(nodes.selectRunNodesForUpdate("r","0")).thenReturn(List.of());AiRunIntervention intervention=new AiRunIntervention();intervention.setId("i");intervention.setRunId("r");intervention.setTenantId("0");intervention.setStatus(InterventionStatus.OPEN.name());intervention.setAllowedActionsJson("[\"CANCEL\"]");when(interventions.selectByIdForUpdate("i","0")).thenReturn(intervention);RunOperationService service=new RunOperationService(runs,nodes,interventions,mock(ExecutionOutboxService.class),mock(RunEventService.class),mock(RunCreationService.class),new ObjectMapper(),mock(RunDependencyAvailabilityService.class));InterventionResolveRequest request=new InterventionResolveRequest();request.setRequestId("request");request.setAction(InterventionAction.CANCEL);
        service.resolve("r","i",request,new AgentAccessContext("admin","0"));
        assertEquals(InterventionStatus.RESOLVED.name(),intervention.getStatus());assertEquals(InterventionAction.CANCEL.name(),intervention.getResolvedAction());assertEquals(RunStatus.CANCELED.name(),run.getStatus());InOrder order=inOrder(interventions,runs);order.verify(interventions).updateById(intervention);order.verify(runs).updateById(run);}

    private static final class Fixture {final AiRunMapper runs=mock(AiRunMapper.class);final AiNodeRunMapper nodes=mock(AiNodeRunMapper.class);final AiRunInterventionMapper interventions=mock(AiRunInterventionMapper.class);final ExecutionOutboxService outbox=mock(ExecutionOutboxService.class);final RunOperationService service;
        Fixture(){AiRun run=new AiRun();run.setId("r");run.setTenantId("0");run.setStatus(RunStatus.CANCELED.name());when(runs.selectByIdForUpdate("r","0")).thenReturn(run);when(nodes.selectRunNodesForUpdate("r","0")).thenReturn(List.of());AiRunIntervention existing=new AiRunIntervention();existing.setId("i");existing.setRunId("r");existing.setTenantId("0");existing.setStatus(InterventionStatus.RESOLVED.name());existing.setResolveRequestId("request");existing.setResolvedAction(InterventionAction.CANCEL.name());existing.setResumeInputJson("null");when(interventions.selectByResolveRequestIdForUpdate("0","request")).thenReturn(existing);service=new RunOperationService(runs,nodes,interventions,outbox,mock(RunEventService.class),mock(RunCreationService.class),new ObjectMapper(),mock(RunDependencyAvailabilityService.class));}
        InterventionResolveRequest request(InterventionAction action){InterventionResolveRequest request=new InterventionResolveRequest();request.setRequestId("request");request.setAction(action);return request;}}
}
