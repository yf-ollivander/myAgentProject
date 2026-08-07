package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.gateway.*;
import org.jeecg.modules.airag.execution.handler.NodeOutcome;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.validation.AgentResultValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.SocketTimeoutException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentNodeHandlerTest {
    @Test void structuredGatewayFailurePreservesRetryability(){assertFailure(AgentExecutionException.retryable("REMOTE_BUSY","try later"),"REMOTE_BUSY",true);}
    @Test void wrappedTimeoutBecomesRetryable(){assertFailure(new RuntimeException(new SocketTimeoutException("private detail")),"AGENT_TIMEOUT",true);}
    @Test void validationFailureKeepsStableCode(){assertFailure(ExecutionException.of(ExecutionErrorCode.ARTIFACT_INVALID,"invalid artifact"),"ARTIFACT_INVALID",false);}

    private void assertFailure(RuntimeException failure,String code,boolean retryable){ObjectMapper mapper=new ObjectMapper();PipelineDefinitionCodec codec=new PipelineDefinitionCodec(mapper);AiRunMapper runs=mock(AiRunMapper.class);AiNodeRunMapper nodes=mock(AiNodeRunMapper.class);NodeCompletionService completion=mock(NodeCompletionService.class);AgentExecutionGateway gateway=mock(AgentExecutionGateway.class);
        AiRun run=new AiRun();run.setId("r");run.setTenantId("0");run.setInputJson("{}");AiNodeRun node=new AiNodeRun();node.setId("nr");node.setNodeId("agent");node.setAttemptNo(0);node.setResumeGeneration(0);PipelineDefinition definition=definition(codec);run.setDefinitionJson(codec.write(definition));when(runs.selectById("r")).thenReturn(run);when(nodes.selectById("nr")).thenReturn(node);when(nodes.selectList(any())).thenReturn(List.of());when(gateway.execute(any())).thenThrow(failure);
        NodeExecutionService service=new NodeExecutionService(runs,nodes,mock(AiArtifactMapper.class),codec,new PipelineTemplateResolver(),gateway,mock(AgentResultValidator.class),completion,mapper);
        service.execute(new NodeClaimService.ClaimedNode("0","r","nr","agent","owner","trace"));
        ArgumentCaptor<NodeOutcome> outcome=ArgumentCaptor.forClass(NodeOutcome.class);verify(completion).complete(any(),outcome.capture());assertEquals(code,outcome.getValue().errorCode());assertEquals(retryable,outcome.getValue().retryable());}

    private PipelineDefinition definition(PipelineDefinitionCodec codec){PipelineDefinition definition=new PipelineDefinition();definition.setSchemaVersion("1.1");PipelineDefinition.PipelineMetadata metadata=new PipelineDefinition.PipelineMetadata();definition.setPipeline(metadata);AgentNodeConfig config=new AgentNodeConfig();config.setAgentId("a");config.setStageCode("stage");config.setInput(Map.of());PipelineNode node=new PipelineNode();node.setId("agent");node.setName("agent");node.setType(PipelineEnums.NodeType.AGENT);node.setConfig(codec.valueToTree(config));definition.setNodes(List.of(node));definition.setEdges(List.of());return definition;}
}
