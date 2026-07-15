package org.jeecg.modules.airag.agent.support;

import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class FeishuMessageEventReceiver {
    private final FeishuMessageEventHandoff handoff;

    public FeishuMessageEventReceiver(FeishuMessageEventHandoff handoff) {
        this.handoff = handoff;
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
                .rootId(message.getRootId()).parentId(message.getParentId()).messageType(message.getMessageType())
                .senderOpenId(data.getSender() == null || data.getSender().getSenderId() == null
                        ? null : data.getSender().getSenderId().getOpenId())
                .content(message.getContent()).build();
        log.info("Feishu message event received: botKey={}, requestId={}, messageId={}, chatId={}, messageType={}",
                inbound.getBotKey(), inbound.getRequestId(), inbound.getMessageId(), inbound.getChatId(),
                inbound.getMessageType());
        if (Boolean.TRUE.equals(bot.getCommandEnabled())) {
            handoff.submit(inbound);
        }
    }
}
