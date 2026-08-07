package org.jeecg.modules.airag.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiAgentMapper;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.service.AgentConnectorInvoker;
import org.jeecg.modules.airag.agent.service.IAiConnectorService;
import org.jeecg.modules.airag.agent.service.IAiFeishuBotService;
import org.jeecg.modules.airag.agent.service.impl.AiAgentServiceImpl;
import org.jeecg.modules.airag.agent.service.impl.AiFeishuBotServiceImpl;
import org.jeecg.modules.airag.agent.support.FeishuBotClient;
import org.jeecg.modules.airag.agent.support.FeishuLongConnectionManager;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiFeishuBotModeServiceTest {
    @Test
    void orchestratorCanBeEnabledWithoutAgentBinding() {
        AiFeishuBotMapper botMapper = mock(AiFeishuBotMapper.class);
        SecretCipherService cipher = mock(SecretCipherService.class);
        FeishuLongConnectionManager connections = mock(FeishuLongConnectionManager.class);
        AiFeishuBot bot = bot(AiConfigDtos.ORCHESTRATOR, true);
        when(botMapper.selectOne(any())).thenReturn(bot);
        when(cipher.isConfigured()).thenReturn(true);
        AiFeishuBotServiceImpl service = new AiFeishuBotServiceImpl(cipher, mock(FeishuBotClient.class),
                mock(AiAgentMapper.class), new AiAgentProperties(), connections);
        ReflectionTestUtils.setField(service, "baseMapper", botMapper);

        service.enable(bot.getId());

        verify(connections).start(bot);
    }

    @Test
    void referencedBotCannotSwitchToOrchestrator() {
        AiFeishuBotMapper botMapper = mock(AiFeishuBotMapper.class);
        AiAgentMapper agentMapper = mock(AiAgentMapper.class);
        AiFeishuBot bot = bot(AiConfigDtos.DIRECT_AGENT, false);
        when(botMapper.selectOne(any())).thenReturn(bot);
        when(botMapper.selectByIdForUpdate(bot.getId())).thenReturn(bot);
        when(agentMapper.selectCount(any())).thenReturn(1L);
        AiFeishuBotServiceImpl service = new AiFeishuBotServiceImpl(mock(SecretCipherService.class),
                mock(FeishuBotClient.class), agentMapper, new AiAgentProperties(),
                mock(FeishuLongConnectionManager.class));
        ReflectionTestUtils.setField(service, "baseMapper", botMapper);
        AiConfigDtos.FeishuBotUpsertRequest request = botRequest(AiConfigDtos.ORCHESTRATOR);

        assertThrows(JeecgBootException.class, () -> service.update(bot.getId(), request));
    }

    @Test
    void agentCannotBindOrchestrator() {
        AiAgentMapper agentMapper = mock(AiAgentMapper.class);
        AiFeishuBotMapper botMapper = mock(AiFeishuBotMapper.class);
        IAiConnectorService connectorService = mock(IAiConnectorService.class);
        IAiFeishuBotService botService = mock(IAiFeishuBotService.class);
        AiFeishuBot bot = bot(AiConfigDtos.ORCHESTRATOR, true);
        when(connectorService.getVisibleEntity("connector-id")).thenReturn(new AiConnector());
        when(botService.getVisibleEntity(bot.getId())).thenReturn(bot);
        when(botMapper.selectByIdForUpdate(bot.getId())).thenReturn(bot);
        AiAgentServiceImpl service = new AiAgentServiceImpl(connectorService, botService, botMapper,
                mock(AgentConnectorInvoker.class), new ObjectMapper(), mock(CommonAPI.class));
        ReflectionTestUtils.setField(service, "baseMapper", agentMapper);
        AiConfigDtos.AgentUpsertRequest request = new AiConfigDtos.AgentUpsertRequest();
        request.setAgentCode("agentCode");
        request.setName("Agent");
        request.setConnectorId("connector-id");
        request.setFeishuBotId(bot.getId());

        assertThrows(JeecgBootException.class, () -> service.create(request));
    }

    private static AiFeishuBot bot(String entryMode, boolean enabled) {
        AiFeishuBot bot = new AiFeishuBot();
        bot.setId("bot-id");
        bot.setBotKey("testBot");
        bot.setAppId("app-id");
        bot.setAppSecretCipher("encrypted-secret");
        bot.setEntryMode(entryMode);
        bot.setCommandEnabled(false);
        bot.setEnabled(enabled);
        return bot;
    }

    private static AiConfigDtos.FeishuBotUpsertRequest botRequest(String entryMode) {
        AiConfigDtos.FeishuBotUpsertRequest request = new AiConfigDtos.FeishuBotUpsertRequest();
        request.setBotKey("testBot");
        request.setName("Test Bot");
        request.setAppId("app-id");
        request.setEntryMode(entryMode);
        request.setCommandEnabled(false);
        return request;
    }
}
