package org.jeecg.modules.airag.agent;

import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.support.FeishuMessageEventHandoff;
import org.jeecg.modules.airag.agent.support.FeishuMessageEventReceiver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuMessageEventReceiverTest {
    @Test
    void handsOffValidatedCommandWithoutLoggingOrTransformingContent() {
        FeishuMessageEventHandoff handoff = mock(FeishuMessageEventHandoff.class);
        FeishuMessageEventReceiver receiver = new FeishuMessageEventReceiver(handoff);
        AiFeishuBot bot = bot(true);
        P2MessageReceiveV1 event = event("message-id", "chat-id", "private-command");

        receiver.accept(bot, event);

        ArgumentCaptor<org.jeecg.modules.airag.agent.support.FeishuInboundMessage> captor =
                ArgumentCaptor.forClass(org.jeecg.modules.airag.agent.support.FeishuInboundMessage.class);
        verify(handoff).submit(captor.capture());
        assertEquals("private-command", captor.getValue().getContent());
        assertEquals(AiConfigDtos.ORCHESTRATOR, captor.getValue().getEntryMode());
    }

    @Test
    void receiveOnlyAndMalformedEventsAreNotSubmitted() {
        FeishuMessageEventHandoff handoff = mock(FeishuMessageEventHandoff.class);
        FeishuMessageEventReceiver receiver = new FeishuMessageEventReceiver(handoff);

        receiver.accept(bot(false), event("message-id", "chat-id", "content"));
        receiver.accept(bot(true), event(null, "chat-id", "content"));

        verify(handoff, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    private static AiFeishuBot bot(boolean commandEnabled) {
        AiFeishuBot bot = new AiFeishuBot();
        bot.setId("bot-id");
        bot.setBotKey("testBot");
        bot.setEntryMode(AiConfigDtos.ORCHESTRATOR);
        bot.setCommandEnabled(commandEnabled);
        return bot;
    }

    private static P2MessageReceiveV1 event(String messageId, String chatId, String content) {
        P2MessageReceiveV1 event = mock(P2MessageReceiveV1.class);
        P2MessageReceiveV1Data data = mock(P2MessageReceiveV1Data.class);
        EventMessage message = mock(EventMessage.class);
        when(event.getRequestId()).thenReturn("request-id");
        when(event.getEvent()).thenReturn(data);
        when(data.getMessage()).thenReturn(message);
        when(message.getMessageId()).thenReturn(messageId);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageType()).thenReturn("text");
        when(message.getContent()).thenReturn(content);
        return event;
    }
}
