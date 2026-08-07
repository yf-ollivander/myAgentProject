package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.execution.gateway.*;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.validation.PipelineGraphAnalyzer;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PipelineFixtureEndToEndTest {
    private static final List<String> FIXTURES=List.of("simple-serial.json","condition-branches.json","five-role-artifacts.json","needs-input.json","fixed-repair.json");
    @Test void fivePublishedShapesTraverseDeterministicallyToAnEnd()throws Exception{ObjectMapper mapper=new ObjectMapper();PipelineDefinitionCodec codec=new PipelineDefinitionCodec(mapper);PipelineGraphAnalyzer graph=new PipelineGraphAnalyzer();MockAgentExecutionGateway gateway=new MockAgentExecutionGateway();for(String fixture:FIXTURES){try(InputStream in=getClass().getResourceAsStream("/pipeline-definition/"+fixture)){assertNotNull(in);PipelineDefinition definition=codec.readDefinition(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));var analysis=graph.analyze(definition);assertTrue(analysis.dag(),fixture);assertEquals(definition.getNodes().size(),analysis.reachable().size(),fixture);String current=definition.getNodes().stream().filter(n->n.getType()==PipelineEnums.NodeType.START).findFirst().orElseThrow().getId();Set<String> visited=new HashSet<>();while(visited.add(current)){PipelineNode node=analysis.nodes().get(current);if(node.getType()==PipelineEnums.NodeType.AGENT){ObjectNode input=mapper.createObjectNode();if(fixture.equals("needs-input.json")&&!visited.contains("resumed")){input.put("__mockStatus","NEEDS_INPUT");var waiting=gateway.execute(new AgentExecutionGateway.AgentExecutionRequest("r:"+current+":0:0","r",current,0,0,"t",null,input,null,List.of()));assertEquals(PipelineEnums.AgentResultStatus.NEEDS_INPUT,waiting.getStatus());visited.add("resumed");}var success=gateway.execute(new AgentExecutionGateway.AgentExecutionRequest("r:"+current+":0:1","r",current,0,1,"t",null,mapper.createObjectNode(),null,List.of()));assertEquals(PipelineEnums.AgentResultStatus.SUCCESS,success.getStatus());}if(node.getType()==PipelineEnums.NodeType.END)break;List<PipelineEdge> outgoing=analysis.outgoing().get(current);PipelineEdge selected=outgoing.stream().filter(e->e.getBranch()==PipelineEnums.EdgeBranch.TRUE).findFirst().orElse(outgoing.get(0));current=selected.getTarget();}assertEquals(PipelineEnums.NodeType.END,analysis.nodes().get(current).getType(),fixture);}}}
}
