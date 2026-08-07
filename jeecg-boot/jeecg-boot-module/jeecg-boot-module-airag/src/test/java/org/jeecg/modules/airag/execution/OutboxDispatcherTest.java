package org.jeecg.modules.airag.execution;

import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.AiOutbox;
import org.jeecg.modules.airag.execution.mapper.AiOutboxMapper;
import org.jeecg.modules.airag.execution.scheduler.OutboxDispatcher;
import org.jeecg.modules.airag.execution.service.OutboxDeadEventService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    @SuppressWarnings({"rawtypes","unchecked"})
    @Test void publishFailureUsesClaimTokenConditionalUpdate(){AiOutboxMapper mapper=mock(AiOutboxMapper.class);StringRedisTemplate redis=mock(StringRedisTemplate.class);StreamOperations operations=mock(StreamOperations.class);JdbcTemplate jdbcTemplate=mock(JdbcTemplate.class);when(redis.opsForStream()).thenReturn(operations);when(operations.add(any(MapRecord.class))).thenThrow(new RuntimeException("offline"));AiOutbox item=new AiOutbox();item.setId("o");item.setTenantId("0");item.setAggregateId("r");item.setDestination(OutboxDestination.TASK.name());item.setPayloadJson("{}");item.setRetryCount(0);when(jdbcTemplate.queryForList(anyString(),eq(String.class),eq(100))).thenReturn(List.of("o"));when(mapper.selectClaimed(anyString())).thenReturn(List.of(item));AiExecutorProperties properties=new AiExecutorProperties();properties.setEnabled(true);
        new OutboxDispatcher(mapper,redis,properties,mock(OutboxDeadEventService.class),jdbcTemplate).dispatch();
        verify(mapper).markFailed(eq("o"),anyString(),eq(OutboxStatus.PENDING.name()),eq(1),any(),eq("offline"));verify(mapper,never()).updateById(any(AiOutbox.class));}
}
