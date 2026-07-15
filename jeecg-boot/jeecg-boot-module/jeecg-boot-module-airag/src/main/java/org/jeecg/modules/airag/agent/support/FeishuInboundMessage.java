package org.jeecg.modules.airag.agent.support;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
public class FeishuInboundMessage {
    String botId;
    String botKey;
    String entryMode;
    String requestId;
    String messageId;
    String chatId;
    String threadId;
    String rootId;
    String parentId;
    String messageType;
    String senderOpenId;
    @JSONField(serialize = false)
    @ToString.Exclude
    String content;
}
