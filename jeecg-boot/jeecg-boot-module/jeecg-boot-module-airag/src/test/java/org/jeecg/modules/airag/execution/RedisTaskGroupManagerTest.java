package org.jeecg.modules.airag.execution;

import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.redis.RedisTaskGroupManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisTaskGroupManagerTest {
    @Test void busyGroupIsReadyAndNoGroupForcesRecreation(){StringRedisTemplate redis=mock(StringRedisTemplate.class);when(redis.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("BUSYGROUP already exists"));AiExecutorProperties properties=new AiExecutorProperties();properties.setEnabled(true);RedisTaskGroupManager manager=new RedisTaskGroupManager(redis,properties);
        assertTrue(manager.ensureGroup());manager.onOperationFailure(new RuntimeException("NOGROUP missing"));assertTrue(manager.ensureGroup());verify(redis,times(2)).execute(any(RedisCallback.class));}
}
