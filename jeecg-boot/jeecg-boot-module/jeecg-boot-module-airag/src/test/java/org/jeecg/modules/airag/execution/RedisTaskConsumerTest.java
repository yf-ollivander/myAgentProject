package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.redis.*;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinitionCodec;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.mockito.Mockito.*;

class RedisTaskConsumerTest {
    @Test void unavailableGroupDoesNotReadOrClaimMessages(){StringRedisTemplate redis=mock(StringRedisTemplate.class);RedisTaskGroupManager groups=mock(RedisTaskGroupManager.class);NodeClaimService claims=mock(NodeClaimService.class);when(groups.ensureGroup()).thenReturn(false);AiExecutorProperties properties=new AiExecutorProperties();properties.setEnabled(true);
        RedisTaskConsumer consumer=new RedisTaskConsumer(redis,properties,mock(AiNodeRunMapper.class),mock(AiRunMapper.class),new PipelineDefinitionCodec(new ObjectMapper()),claims,mock(NodeExecutionService.class),mock(ThreadPoolTaskExecutor.class),groups,new ExecutionTenantScope());consumer.poll();verify(redis,never()).opsForStream();verifyNoInteractions(claims);}
}
