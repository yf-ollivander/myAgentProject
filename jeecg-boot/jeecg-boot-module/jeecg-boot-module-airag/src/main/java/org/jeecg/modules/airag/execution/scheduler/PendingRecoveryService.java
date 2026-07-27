package org.jeecg.modules.airag.execution.scheduler;

import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.redis.RedisTaskConsumer;
import org.jeecg.modules.airag.execution.redis.RedisTaskGroupManager;
import org.springframework.data.redis.core.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class PendingRecoveryService {
    private final StringRedisTemplate redis;private final AiExecutorProperties properties;private final RedisTaskConsumer consumer;private final RedisTaskGroupManager groups;
    public PendingRecoveryService(StringRedisTemplate redis,AiExecutorProperties properties,RedisTaskConsumer consumer,RedisTaskGroupManager groups){this.redis=redis;this.properties=properties;this.consumer=consumer;this.groups=groups;}
    @Scheduled(fixedDelayString="#{${ai.executor.pending-scan-seconds:30} * 1000}")
    public void recover(){if(!properties.isEnabled()||!groups.ensureGroup())return;try{Object result=redis.execute((RedisCallback<Object>)connection->connection.execute("XPENDING",b(properties.getTaskStream()),b(properties.getTaskGroup()),b("-"),b("+"),b(String.valueOf(properties.getPendingClaimBatchSize()))));
            for(String id:pendingIds(result))redis.execute((RedisCallback<Object>)connection->connection.execute("XCLAIM",b(properties.getTaskStream()),b(properties.getTaskGroup()),b(consumer.recoveryConsumerName()),b(String.valueOf(properties.getPendingIdleSeconds()*1000L)),b(id)));}catch(Exception failure){groups.onOperationFailure(failure);}}
    private List<String> pendingIds(Object value){List<String> ids=new ArrayList<>();if(value instanceof List<?> rows)for(Object row:rows)if(row instanceof List<?> parts&&!parts.isEmpty()){Object id=parts.get(0);ids.add(id instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(id));}return ids;}
    private byte[] b(String value){return value.getBytes(StandardCharsets.UTF_8);}
}
