package org.jeecg.modules.airag.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.event.cardcallback.model.*;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.FeishuInboundCardAction;
import org.jeecg.modules.airag.collaboration.inbox.*;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FeishuCardActionEventReceiver {
    private final FeishuInboundEventIngress ingress;private final FeishuInboundEventHandoff handoff;private final ObjectMapper mapper;
    public FeishuCardActionEventReceiver(FeishuInboundEventIngress ingress,FeishuInboundEventHandoff handoff,ObjectMapper mapper){this.ingress=ingress;this.handoff=handoff;this.mapper=mapper;}
    public P2CardActionTriggerResponse accept(AiFeishuBot bot,P2CardActionTrigger event){P2CardActionTriggerData data=event.getEvent();CallBackAction action=data==null?null:data.getAction();CallBackContext context=data==null?null:data.getContext();String eventId=event.getHeader()==null?event.getRequestId():event.getHeader().getEventId();FeishuInboundCardAction inbound=FeishuInboundCardAction.builder().botId(bot.getId()).eventId(eventId).operatorOpenId(data==null||data.getOperator()==null?null:data.getOperator().getOpenId()).messageId(context==null?null:context.getOpenMessageId()).chatId(context==null?null:context.getOpenChatId()).token(data==null?null:data.getToken()).actionTag(action==null?null:action.getTag()).actionValue(nodes(action==null?null:action.getValue())).formValue(nodes(action==null?null:action.getFormValue())).inputValue(action==null?null:action.getInputValue()).build();String id=ingress.persistCardAction(inbound);handoff.submit(id);return new P2CardActionTriggerResponse();}
    private Map<String,JsonNode> nodes(Map<String,Object> values){Map<String,JsonNode> result=new LinkedHashMap<>();if(values!=null)values.forEach((key,value)->result.put(key,mapper.valueToTree(value)));return result;}
}
