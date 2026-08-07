package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.handler.NodeOutcome;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NodeProgressionServiceTest {
    @Test
    void successfulDefaultEdgeMakesSuccessorReadyOnce() {
        ObjectMapper mapper=new ObjectMapper();PipelineDefinitionCodec codec=new PipelineDefinitionCodec(mapper);AiRunMapper runs=mock(AiRunMapper.class);AiNodeRunMapper nodes=mock(AiNodeRunMapper.class);ExecutionOutboxService outbox=mock(ExecutionOutboxService.class);RunDependencyAvailabilityService dependencies=mock(RunDependencyAvailabilityService.class);
        AiRun run=new AiRun();run.setId("r");run.setTenantId("0");run.setStatus(RunStatus.RUNNING.name());run.setEventSequence(0L);run.setDefinitionJson(codec.write(definition(codec)));
        AiNodeRun start=node("nr1","start",NodeStatus.RUNNING,0);start.setLeaseOwner("owner");AiNodeRun end=node("nr2","end",NodeStatus.PENDING,1);
        when(runs.selectByIdForUpdate("r","0")).thenReturn(run);when(nodes.selectRunNodesForUpdate("r","0")).thenReturn(List.of(start,end));when(dependencies.available(run)).thenReturn(true);
        NodeCompletionService service=new NodeCompletionService(runs,nodes,mock(AiArtifactMapper.class),mock(AiRunInterventionMapper.class),codec,outbox,mock(RunEventService.class),dependencies,mapper);

        service.complete(new NodeClaimService.ClaimedNode("0","r","nr1","start","owner","trace"),NodeOutcome.success(NullNode.instance,"done",PipelineEnums.EdgeBranch.DEFAULT.name()));

        verify(outbox,times(1)).nodeReady(eq(run),eq(end),eq("trace"),any(Date.class));
    }

    private AiNodeRun node(String id,String nodeId,NodeStatus status,int incoming){AiNodeRun node=new AiNodeRun();node.setId(id);node.setTenantId("0");node.setRunId("r");node.setNodeId(nodeId);node.setNodeType(nodeId.equals("start")?"START":"END");node.setStatus(status.name());node.setIncomingCount(incoming);node.setResolvedIncomingCount(0);node.setSelectedIncomingCount(0);node.setDispatchVersion(0L);node.setRetryCount(0);return node;}
    private PipelineDefinition definition(PipelineDefinitionCodec codec){PipelineDefinition d=new PipelineDefinition();d.setSchemaVersion("1.1");d.setPipeline(new PipelineDefinition.PipelineMetadata());PipelineNode start=node(codec,"start",PipelineEnums.NodeType.START,new StartNodeConfig());PipelineNode end=node(codec,"end",PipelineEnums.NodeType.END,new EndNodeConfig());PipelineEdge edge=new PipelineEdge();edge.setId("e");edge.setSource("start");edge.setTarget("end");edge.setBranch(PipelineEnums.EdgeBranch.DEFAULT);d.setNodes(List.of(start,end));d.setEdges(List.of(edge));return d;}
    private PipelineNode node(PipelineDefinitionCodec codec,String id,PipelineEnums.NodeType type,Object config){PipelineNode n=new PipelineNode();n.setId(id);n.setName(id);n.setType(type);n.setConfig(codec.valueToTree(config));return n;}
}
