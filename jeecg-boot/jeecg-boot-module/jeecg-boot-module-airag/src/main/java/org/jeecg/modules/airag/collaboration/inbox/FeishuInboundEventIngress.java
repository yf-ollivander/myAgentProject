package org.jeecg.modules.airag.collaboration.inbox;

import org.jeecg.modules.airag.agent.support.FeishuInboundMessage;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.FeishuInboundCardAction;

public interface FeishuInboundEventIngress {
    String persistMessage(FeishuInboundMessage message);
    String persistCardAction(FeishuInboundCardAction action);
}
