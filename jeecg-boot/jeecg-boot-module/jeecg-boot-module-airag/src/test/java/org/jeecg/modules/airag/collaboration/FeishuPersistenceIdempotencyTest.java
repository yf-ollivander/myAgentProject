package org.jeecg.modules.airag.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.support.FeishuInboundMessage;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.DeliveryMessageType;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.DeliveryTargetType;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuDelivery;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuInboundEvent;
import org.jeecg.modules.airag.collaboration.inbox.FeishuInboundIngressService;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuDeliveryMapper;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuInboundEventMapper;
import org.jeecg.modules.airag.collaboration.notification.FeishuDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeishuPersistenceIdempotencyTest {
    @Test
    void duplicateInboundEventWithSamePayloadReturnsOriginalUuid() {
        ObjectMapper mapper = new ObjectMapper();
        SecretCipherService cipher = cipher();
        AiFeishuInboundEventMapper events = mock(AiFeishuInboundEventMapper.class);
        AiFeishuBotMapper bots = mock(AiFeishuBotMapper.class);
        AiFeishuBot bot = bot();
        when(bots.selectById("bot-1")).thenReturn(bot);
        AtomicReference<AiFeishuInboundEvent> existing = new AtomicReference<>();
        doAnswer(invocation -> {
            AiFeishuInboundEvent inserted = invocation.getArgument(0);
            AiFeishuInboundEvent duplicate = new AiFeishuInboundEvent();
            duplicate.setId("original-inbound-id");
            duplicate.setPayloadHash(inserted.getPayloadHash());
            existing.set(duplicate);
            throw new DuplicateKeyException("duplicate");
        }).when(events).insert(any(AiFeishuInboundEvent.class));
        when(events.selectByEventKey("MSG:bot-1:message-1")).thenAnswer(ignored -> existing.get());

        String id = new FeishuInboundIngressService(events, bots, cipher, mapper).persistMessage(message());

        assertEquals("original-inbound-id", id);
    }

    @Test
    void duplicateDeliveryWithDifferentContentIsRejected() {
        AiFeishuDeliveryMapper deliveries = mock(AiFeishuDeliveryMapper.class);
        doThrow(new DuplicateKeyException("duplicate")).when(deliveries).insert(any(AiFeishuDelivery.class));
        AiFeishuDelivery existing = new AiFeishuDelivery();
        existing.setEventId("event-1");
        existing.setContentHash("different");
        when(deliveries.selectOne(any())).thenReturn(existing);
        FeishuDeliveryService service = new FeishuDeliveryService(deliveries, cipher(), new ObjectMapper());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.enqueue("event-1", "0", "run-1", "RUN_STARTED", 1L, null,
                        "bot-1", DeliveryTargetType.REPLY, "message-1", DeliveryMessageType.TEXT,
                        new ObjectMapper().createObjectNode().put("text", "new")));

        assertTrue(error.getMessage().contains("conflicts"));
    }

    private SecretCipherService cipher() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setSecretKey("0123456789abcdef0123456789abcdef");
        return new SecretCipherService(properties);
    }

    private AiFeishuBot bot() {
        AiFeishuBot bot = new AiFeishuBot();
        bot.setId("bot-1");
        bot.setTenantId("0");
        bot.setEnabled(true);
        bot.setCommandEnabled(true);
        return bot;
    }

    private FeishuInboundMessage message() {
        return FeishuInboundMessage.builder().botId("bot-1").messageId("message-1")
                .senderOpenId("open-1").messageType("text").content("{\"text\":\"help\"}")
                .mentionKeys(List.of()).build();
    }
}
