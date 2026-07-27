package org.jeecg.modules.airag.execution.scheduler;

import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.AiOutbox;
import org.jeecg.modules.airag.execution.mapper.AiOutboxMapper;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.jeecg.modules.airag.execution.service.OutboxDeadEventService;
import java.util.*;

@Component
public class OutboxDispatcher {
    private static final int[] BACKOFF={1,2,5,10,30};
    private final AiOutboxMapper mapper;private final StringRedisTemplate redis;private final AiExecutorProperties properties;private final OutboxDeadEventService deadEvents;
    public OutboxDispatcher(AiOutboxMapper mapper,StringRedisTemplate redis,AiExecutorProperties properties,OutboxDeadEventService deadEvents){this.mapper=mapper;this.redis=redis;this.properties=properties;this.deadEvents=deadEvents;}
    @Scheduled(fixedDelayString="${ai.executor.outbox-interval-ms:1000}")
    public void dispatch(){if(!properties.isEnabled())return;String token=UUID.randomUUID().toString();List<String> ids=mapper.selectClaimCandidates(properties.getOutboxBatchSize());if(ids.isEmpty())return;mapper.claim(ids,token,properties.getOutboxClaimSeconds());
        for(AiOutbox outbox:mapper.selectClaimed(token)){try{String stream=OutboxDestination.TASK.name().equals(outbox.getDestination())?properties.getTaskStream():properties.getNotificationStream();Map<String,String> fields=new LinkedHashMap<>();
                com.fasterxml.jackson.databind.JsonNode payload=new com.fasterxml.jackson.databind.ObjectMapper().readTree(outbox.getPayloadJson());payload.fields().forEachRemaining(e->fields.put(e.getKey(),e.getValue().isTextual()?e.getValue().asText():e.getValue().toString()));
                redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(fields));mapper.markSent(outbox.getId(),token);
            }catch(Exception e){int retry=outbox.getRetryCount()+1;String status=retry>=20?OutboxStatus.DEAD.name():OutboxStatus.PENDING.name();Date nextRetryAt=new Date(System.currentTimeMillis()+1000L*BACKOFF[Math.min(retry-1,BACKOFF.length-1)]);String error=abbreviate(e.getMessage());
                // The claim token prevents a late publisher from overwriting a record reclaimed by another dispatcher.
                if(mapper.markFailed(outbox.getId(),token,status,retry,nextRetryAt,error)==1&&retry>=20){outbox.setStatus(status);outbox.setRetryCount(retry);outbox.setLastError(error);deadEvents.record(outbox);}}}}
    private String abbreviate(String s){return s==null?"Redis publish failed":s.length()>1000?s.substring(0,1000):s;}
}
