package org.jeecg.modules.airag.collaboration;

import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.BindingTokenResult;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuBindingToken;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuBindingTokenMapper;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuUserBindingMapper;
import org.jeecg.modules.airag.collaboration.service.FeishuAccessService;
import org.jeecg.modules.airag.collaboration.service.FeishuBindingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeishuBindingTokenTest {
    @Test
    void createsSingleUse128BitBase32TokenWithoutPersistingPlaintext() {
        AiFeishuUserBindingMapper bindings = mock(AiFeishuUserBindingMapper.class);
        AiFeishuBindingTokenMapper tokens = mock(AiFeishuBindingTokenMapper.class);
        FeishuAccessService access = mock(FeishuAccessService.class);
        LoginUser user = new LoginUser();
        user.setId("user-1");
        user.setUsername("admin");
        when(access.requireUser("admin", "0")).thenReturn(user);
        CollaborationProperties properties = new CollaborationProperties();
        properties.setBindingTokenMinutes(10);
        FeishuBindingService service = new FeishuBindingService(bindings, tokens, access, properties);
        long before = System.currentTimeMillis();

        BindingTokenResult result = service.createToken("bot-1", new AgentAccessContext("admin", "0"));

        assertTrue(result.code().matches("[A-Z2-7]{26}"));
        assertEquals("绑定【" + result.code() + "】", result.command());
        assertTrue(result.expiresAt().after(new Date(before + 9 * 60_000L)));
        ArgumentCaptor<AiFeishuBindingToken> captor = ArgumentCaptor.forClass(AiFeishuBindingToken.class);
        verify(tokens).insert(captor.capture());
        assertEquals(64, captor.getValue().getTokenHash().length());
        assertNotEquals(result.code(), captor.getValue().getTokenHash());
    }
}
