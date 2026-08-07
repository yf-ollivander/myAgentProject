package org.jeecg.modules.airag.agent.support;

import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.collaboration.inbox.FeishuInboundEventHandoff;
import org.jeecg.modules.airag.collaboration.inbox.FeishuInboundEventIngress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class FeishuMessageEventReceiver {
    private final FeishuMessageEventHandoff handoff;
    private final FeishuInboundEventIngress persistentIngress;
    private final FeishuInboundEventHandoff persistentHandoff;

    public FeishuMessageEventReceiver(FeishuMessageEventHandoff handoff) {
        this(handoff, (FeishuInboundEventIngress) null, (FeishuInboundEventHandoff) null);
    }

    @Autowired
    public FeishuMessageEventReceiver(FeishuMessageEventHandoff handoff,
                                      ObjectProvider<FeishuInboundEventIngress> ingressProvider,
                                      ObjectProvider<FeishuInboundEventHandoff> handoffProvider) {
        this(handoff, ingressProvider.getIfAvailable(), handoffProvider.getIfAvailable());
    }

    private FeishuMessageEventReceiver(FeishuMessageEventHandoff handoff,
                                       FeishuInboundEventIngress persistentIngress,
                                       FeishuInboundEventHandoff persistentHandoff) {
        this.handoff = handoff; this.persistentIngress = persistentIngress; this.persistentHandoff = persistentHandoff;
    }

    public void accept(AiFeishuBot bot, P2MessageReceiveV1 event) {
        P2MessageReceiveV1Data data = event == null ? null : event.getEvent();
        EventMessage message = data == null ? null : data.getMessage();
        if (bot == null || !StringUtils.hasText(bot.getId()) || !StringUtils.hasText(bot.getBotKey())
                || message == null || !StringUtils.hasText(message.getMessageId())
                || !StringUtils.hasText(message.getChatId()) || !StringUtils.hasText(message.getMessageType())) {
            log.warn("Ignored invalid Feishu message event: botKey={}, requestId={}",
                    bot == null ? null : bot.getBotKey(), event == null ? null : event.getRequestId());
            return;
        }
        FeishuInboundMessage inbound = FeishuInboundMessage.builder()
                .botId(bot.getId()).botKey(bot.getBotKey()).entryMode(bot.getEntryMode())
                .requestId(event.getRequestId()).messageId(message.getMessageId()).chatId(message.getChatId())
                .threadId(message.getThreadId())
                .rootId(message.getRootId()).parentId(message.getParentId())
                .chatType(reflectString(message, "getChatType"))
                .senderType(reflectString(data.getSender(), "getSenderType"))
                .messageType(message.getMessageType()).mentionKeys(reflectMentionKeys(message))
                .senderOpenId(data.getSender() == null || data.getSender().getSenderId() == null
                        ? null : data.getSender().getSenderId().getOpenId())
                .content(message.getContent()).build();
        log.info("Feishu message event received: botKey={}, requestId={}, messageId={}, chatId={}, messageType={}",
                inbound.getBotKey(), inbound.getRequestId(), inbound.getMessageId(), inbound.getChatId(),
                inbound.getMessageType());
        if (Boolean.TRUE.equals(bot.getCommandEnabled())) {
            if (persistentIngress != null && persistentHandoff != null) {
                // Persistence happens on the SDK callback thread so a full handoff queue cannot lose the event.
                String eventId = persistentIngress.persistMessage(inbound);
                persistentHandoff.submit(eventId);
            } else {
                handoff.submit(inbound);
            }
        }
    }

    private String reflectString(Object target, String method) {
        if (target == null) return null;
        try { Object value = target.getClass().getMethod(method).invoke(target); return value == null ? null : value.toString(); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    private java.util.List<String> reflectMentionKeys(Object message) {
        try {
            Object mentions = message.getClass().getMethod("getMentions").invoke(message);
            if (!(mentions instanceof Object[] values)) return java.util.List.of();
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (Object value : values) {
                String key = reflectString(value, "getKey");
                if (StringUtils.hasText(key)) keys.add(key);
            }
            return keys;
        } catch (ReflectiveOperationException ignored) { return java.util.List.of(); }
    }
}
