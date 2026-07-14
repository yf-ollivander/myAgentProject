package org.jeecg.modules.airag.agent.support;

import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FeishuMessageEventReceiver {
    public void accept(String botKey, P2MessageReceiveV1 event) {
        P2MessageReceiveV1Data data = event == null ? null : event.getEvent();
        EventMessage message = data == null ? null : data.getMessage();

        // Intentionally stop at ingestion: Agent lookup, deduplication, run creation, and replies are future work.
        log.info("Feishu message event received; downstream handling is pending: "
                        + "botKey={}, requestId={}, messageId={}, chatId={}, messageType={}",
                botKey,
                event == null ? null : event.getRequestId(),
                message == null ? null : message.getMessageId(),
                message == null ? null : message.getChatId(),
                message == null ? null : message.getMessageType());
    }
}
