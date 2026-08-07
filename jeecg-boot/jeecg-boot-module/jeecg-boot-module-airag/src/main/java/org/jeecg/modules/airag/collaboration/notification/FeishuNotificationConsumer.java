package org.jeecg.modules.airag.collaboration.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.*;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.BusinessNotificationEvent;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuSession;
import org.jeecg.modules.airag.collaboration.feishu.FeishuCardSigner;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuSessionMapper;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.RunSource;
import org.jeecg.modules.airag.execution.service.RunNotificationViewProvider;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class FeishuNotificationConsumer {
    private final StringRedisTemplate redis;private final CollaborationProperties properties;
    private final FeishuNotificationGroupManager groups;private final RunNotificationViewProvider views;
    private final AiFeishuSessionMapper sessions;private final FeishuDeliveryService deliveries;
    private final FeishuCardSigner signer;private final ObjectMapper mapper;private final String consumer;
    public FeishuNotificationConsumer(StringRedisTemplate redis,CollaborationProperties properties,
            FeishuNotificationGroupManager groups,RunNotificationViewProvider views,AiFeishuSessionMapper sessions,
            FeishuDeliveryService deliveries,FeishuCardSigner signer,ObjectMapper mapper){this.redis=redis;this.properties=properties;this.groups=groups;this.views=views;this.sessions=sessions;this.deliveries=deliveries;this.signer=signer;this.mapper=mapper;this.consumer=name();}

    @Scheduled(fixedDelay=200)
    public void poll(){if(!groups.ensureGroup())return;try{List<MapRecord<String,Object,Object>> records=redis.opsForStream().read(Consumer.from(properties.getNotificationGroup(),consumer),StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),StreamOffset.create(properties.getNotificationStream(),ReadOffset.lastConsumed()));if(records==null)return;for(MapRecord<String,Object,Object> record:records)consume(record);}catch(Exception e){groups.failed(e);}}

    private void consume(MapRecord<String,Object,Object> record){Map<Object,Object> fields=record.getValue();BusinessNotificationEvent event=new BusinessNotificationEvent(value(fields,"eventId"),value(fields,"eventType"),value(fields,"runId"),value(fields,"nodeRunId"),value(fields,"tenantId"),value(fields,"traceId"),longValue(fields,"eventSequence"),value(fields,"interventionId"));
        RunNotificationViewProvider.NotificationSnapshot snapshot=views.build(event);AiFeishuSession session=sessions.selectByRunId(event.runId());String botId=snapshot.source()==RunSource.FEISHU?snapshot.sourceBotId():snapshot.notificationBotId();String target=snapshot.source()==RunSource.FEISHU&&session!=null?session.getRootMessageId():snapshot.defaultChatId();
        if(!StringUtils.hasText(botId)||!StringUtils.hasText(target))throw new IllegalStateException("Notification target is unavailable");
        if("USER_INPUT_REQUIRED".equals(event.eventType())&&snapshot.intervention()==null){deliveries.skip(event.eventId(),event.tenantId(),event.runId(),event.eventType(),event.eventSequence(),event.interventionId(),botId,target);ack(record.getId());return;}
        DeliveryMessageType type="USER_INPUT_REQUIRED".equals(event.eventType())?DeliveryMessageType.CARD:DeliveryMessageType.TEXT;
        deliveries.enqueue(event.eventId(),event.tenantId(),event.runId(),event.eventType(),event.eventSequence(),event.interventionId(),botId,snapshot.source()==RunSource.FEISHU?DeliveryTargetType.REPLY:DeliveryTargetType.CHAT,target,type,render(event,snapshot,botId));ack(record.getId());}

    private ObjectNode render(BusinessNotificationEvent event,RunNotificationViewProvider.NotificationSnapshot snapshot,String botId){ObjectNode content=mapper.createObjectNode();String text=event.eventType()+"\nrunId="+event.runId();if(snapshot.summary()!=null&&StringUtils.hasText(snapshot.summary().finalSummary()))text+="\n"+snapshot.summary().finalSummary();content.put("text",text);if(!"USER_INPUT_REQUIRED".equals(event.eventType()))return content;
        ObjectNode action=mapper.createObjectNode();action.put("botId",botId);action.put("runId",event.runId());action.put("interventionId",event.interventionId());action.put("resumeToken",snapshot.intervention().resumeToken());action.put("action","SUPPLY_INPUT");action.put("signature",signer.sign(action));content.set("actionValue",action);ArrayNode allowed=content.putArray("allowedActions");snapshot.intervention().allowedActions().forEach(value->allowed.add(value.name()));content.put("prompt",snapshot.intervention().prompt());return content;}
    private void ack(RecordId id){redis.opsForStream().acknowledge(properties.getNotificationStream(),properties.getNotificationGroup(),id);}

    @Scheduled(fixedDelay=30000)
    public void recover(){if(!groups.ensureGroup())return;try{Object result=redis.execute((RedisCallback<Object>)c->c.execute("XPENDING",b(properties.getNotificationStream()),b(properties.getNotificationGroup()),b("-"),b("+"),b("50")));List<String> pending=ids(result);if(pending.isEmpty())return;RecordId[] recordIds=pending.stream().map(RecordId::of).toArray(RecordId[]::new);List<MapRecord<String,Object,Object>> claimed=redis.opsForStream().claim(properties.getNotificationStream(),properties.getNotificationGroup(),consumer,Duration.ofSeconds(properties.getNotificationPendingIdleSeconds()),recordIds);if(claimed!=null)for(MapRecord<String,Object,Object> record:claimed)consume(record);}catch(Exception e){groups.failed(e);}}
    private List<String> ids(Object value){List<String> ids=new ArrayList<>();if(value instanceof List<?> rows)for(Object row:rows)if(row instanceof List<?> parts&&!parts.isEmpty()){Object id=parts.get(0);ids.add(id instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(id));}return ids;}
    private String value(Map<Object,Object> values,String key){Object value=values.get(key);if(value==null)for(Map.Entry<Object,Object> entry:values.entrySet()){Object candidate=entry.getKey();String decoded=candidate instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(candidate);if(key.equals(decoded)){value=entry.getValue();break;}}return value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):value==null?null:String.valueOf(value);}
    private long longValue(Map<Object,Object> values,String key){try{return Long.parseLong(value(values,key));}catch(Exception e){return 0;}}
    private byte[] b(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private String name(){try{return InetAddress.getLocalHost().getHostName()+"-"+ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+"-"+UUID.randomUUID();}catch(Exception e){return"notifier-"+UUID.randomUUID();}}
}
