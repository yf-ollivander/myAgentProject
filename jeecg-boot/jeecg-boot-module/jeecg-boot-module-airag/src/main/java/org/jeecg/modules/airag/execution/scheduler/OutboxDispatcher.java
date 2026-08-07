package org.jeecg.modules.airag.execution.scheduler;

import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.AiOutbox;
import org.jeecg.modules.airag.execution.mapper.AiOutboxMapper;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.jeecg.modules.airag.execution.service.OutboxDeadEventService;
import java.util.*;

@Component
public class OutboxDispatcher {
    private static final int[] BACKOFF={1,2,5,10,30};
    private static final String CLAIM_CANDIDATES_SQL="SELECT id FROM ai_outbox WHERE next_retry_at<=NOW(3) AND (status='PENDING' OR (status='CLAIMED' AND claimed_until<NOW(3))) ORDER BY next_retry_at,id LIMIT ?";
    private final AiOutboxMapper mapper;private final StringRedisTemplate redis;private final AiExecutorProperties properties;private final OutboxDeadEventService deadEvents;private final JdbcTemplate jdbcTemplate;
    public OutboxDispatcher(AiOutboxMapper mapper,StringRedisTemplate redis,AiExecutorProperties properties,OutboxDeadEventService deadEvents,JdbcTemplate jdbcTemplate){this.mapper=mapper;this.redis=redis;this.properties=properties;this.deadEvents=deadEvents;this.jdbcTemplate=jdbcTemplate;}
    @Scheduled(fixedDelayString="${ai.executor.outbox-interval-ms:1000}")
    public void dispatch(){if(!properties.isEnabled())return;String token=UUID.randomUUID().toString();List<String> ids=selectClaimCandidates();if(ids.isEmpty())return;mapper.claim(ids,token,properties.getOutboxClaimSeconds());
        for(AiOutbox outbox:mapper.selectClaimed(token)){try{String stream=OutboxDestination.TASK.name().equals(outbox.getDestination())?properties.getTaskStream():properties.getNotificationStream();Map<String,String> fields=new LinkedHashMap<>();
                com.fasterxml.jackson.databind.JsonNode payload=new com.fasterxml.jackson.databind.ObjectMapper().readTree(outbox.getPayloadJson());payload.fields().forEachRemaining(e->fields.put(e.getKey(),e.getValue().isTextual()?e.getValue().asText():e.getValue().toString()));
                redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(fields));mapper.markSent(outbox.getId(),token);
            }catch(Exception e){int retry=outbox.getRetryCount()+1;String status=retry>=20?OutboxStatus.DEAD.name():OutboxStatus.PENDING.name();Date nextRetryAt=new Date(System.currentTimeMillis()+1000L*BACKOFF[Math.min(retry-1,BACKOFF.length-1)]);String error=abbreviate(e.getMessage());
                // The claim token prevents a late publisher from overwriting a record reclaimed by another dispatcher.
                if(mapper.markFailed(outbox.getId(),token,status,retry,nextRetryAt,error)==1&&retry>=20){outbox.setStatus(status);outbox.setRetryCount(retry);outbox.setLastError(error);deadEvents.record(outbox);}}}}
    // Keep only this empty-poll query outside MyBatis so StdOutImpl can still print useful business SQL.
    private List<String> selectClaimCandidates(){return jdbcTemplate.queryForList(CLAIM_CANDIDATES_SQL,String.class,properties.getOutboxBatchSize());}
    private String abbreviate(String s){return s==null?"Redis publish failed":s.length()>1000?s.substring(0,1000):s;}
}
