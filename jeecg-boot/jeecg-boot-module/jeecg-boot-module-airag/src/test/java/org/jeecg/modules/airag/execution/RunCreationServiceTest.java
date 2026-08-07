package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
import org.jeecg.modules.airag.agent.support.AgentConfigSnapshotSanitizer;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.RunType;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.RunCreateRequest;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.RunSourceContext;
import org.jeecg.modules.airag.execution.entity.AiRun;
import org.jeecg.modules.airag.execution.gateway.AgentExecutionGateway;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.contract.*;
import org.jeecg.modules.airag.pipeline.service.PublishedPipelineProvider;
import org.jeecg.modules.airag.pipeline.validation.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RunCreationServiceTest {
    @Test
    void directRunPersistsOnlySanitizedAgentHeaders() {
        ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();PipelineDefinitionCodec codec=new PipelineDefinitionCodec(mapper);
        AuthorizedAgentConfigProvider agents=mock(AuthorizedAgentConfigProvider.class);AiFeishuBotMapper bots=mock(AiFeishuBotMapper.class);
        AgentConfigSnapshot snapshot=AgentConfigSnapshot.builder().agentId("agent-1").agentCode("agent")
                .timeoutSeconds(30).maxRetry(1).connector(AgentConfigSnapshot.ConnectorSnapshot.builder()
                        .connectorId("connector-1").resultContractVersion("1.1")
                        .requestHeaders(Map.of("Api-Token","secret","X-Trace","visible")).build())
                .feishuBot(AgentConfigSnapshot.FeishuBotSnapshot.builder().botId("bot-1").build()).build();
        when(agents.resolveEnabledSnapshot(any(),any())).thenReturn(snapshot);AiFeishuBot bot=new AiFeishuBot();bot.setId("bot-1");bot.setEntryMode("DIRECT_AGENT");bot.setDefaultChatId("chat");when(bots.selectOne(any())).thenReturn(bot);
        AiRunMapper runs=mock(AiRunMapper.class);AiExecutorProperties properties=new AiExecutorProperties();properties.setEnabled(true);
        RunCreationService service=new RunCreationService(mock(PublishedPipelineProvider.class),agents,bots,codec,
                new PipelineDefinitionNormalizer(codec,new PipelineNodeConfigParser(codec)),runs,mock(AiNodeRunMapper.class),
                mock(AiRunDependencyMapper.class),mock(RunEventService.class),mock(ExecutionOutboxService.class),mapper,
                properties,mock(AgentExecutionGateway.class),new AgentConfigSnapshotSanitizer());
        RunCreateRequest request=new RunCreateRequest();request.setRequestId("request-1");request.setRunType(RunType.AGENT_DIRECT);request.setAgentId("agent-1");ObjectNode input=mapper.createObjectNode();input.put("task","test");request.setInput(input);

        service.startDirectAgent(request,new AgentAccessContext("admin","0"),RunSourceContext.jeecg());

        ArgumentCaptor<AiRun> captor=ArgumentCaptor.forClass(AiRun.class);verify(runs).insert(captor.capture());PipelineDefinition definition=codec.readDefinition(captor.getValue().getDefinitionJson());PipelineNode agentNode=definition.getNodes().stream().filter(n->n.getType()==PipelineEnums.NodeType.AGENT).findFirst().orElseThrow();Map<String,String> headers=codec.parseNodeConfig(agentNode,AgentNodeConfig.class).getAgentSnapshot().getConnector().getRequestHeaders();assertEquals(Map.of("X-Trace","visible"),headers);
    }
}
