package org.jeecg.modules.airag.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuDelivery;
import org.jeecg.modules.airag.collaboration.feishu.FeishuCardSigner;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuSessionMapper;
import org.jeecg.modules.airag.collaboration.notification.FeishuDeliveryService;
import org.jeecg.modules.airag.collaboration.notification.FeishuNotificationConsumer;
import org.jeecg.modules.airag.collaboration.notification.FeishuNotificationGroupManager;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.RunSource;
import org.jeecg.modules.airag.execution.service.RunNotificationViewProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeishuNotificationRecoveryTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void claimedPendingRecordIsPersistedBeforeAck() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn((StreamOperations) streams);
        FeishuNotificationGroupManager groups = mock(FeishuNotificationGroupManager.class);
        when(groups.ensureGroup()).thenReturn(true);
        CollaborationProperties properties = new CollaborationProperties();
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId recordId = RecordId.of("1-0");
        Map<Object, Object> fields = new LinkedHashMap<>();
        fields.put("eventId".getBytes(StandardCharsets.UTF_8), "event-1".getBytes(StandardCharsets.UTF_8));
        fields.put("eventType", "RUN_STARTED");
        fields.put("runId", "run-1");
        fields.put("tenantId", "0");
        fields.put("eventSequence", "1");
        when(record.getValue()).thenReturn(fields);
        when(record.getId()).thenReturn(recordId);
        when(redis.execute(any(RedisCallback.class))).thenReturn(
                List.of(List.of("1-0".getBytes(StandardCharsets.UTF_8))));
        when(streams.claim(eq(properties.getNotificationStream()), eq(properties.getNotificationGroup()),
                anyString(), any(Duration.class), any(RecordId[].class))).thenReturn(List.of(record));
        RunNotificationViewProvider views = mock(RunNotificationViewProvider.class);
        when(views.build(any())).thenReturn(new RunNotificationViewProvider.NotificationSnapshot(
                "0", "run-1", RunSource.JEECG, null, null, null, "bot-1", "chat-1", null, null));
        FeishuDeliveryService deliveries = mock(FeishuDeliveryService.class);
        when(deliveries.enqueue(anyString(), anyString(), anyString(), anyString(), anyLong(), nullable(String.class),
                anyString(), any(), anyString(), any(), any())).thenReturn(new AiFeishuDelivery());
        FeishuNotificationConsumer consumer = new FeishuNotificationConsumer(redis, properties, groups, views,
                mock(AiFeishuSessionMapper.class), deliveries, mock(FeishuCardSigner.class), new ObjectMapper());

        consumer.recover();

        verify(deliveries).enqueue(eq("event-1"), eq("0"), eq("run-1"), eq("RUN_STARTED"), eq(1L),
                isNull(), eq("bot-1"), any(), eq("chat-1"), any(), any());
        verify(streams).acknowledge(properties.getNotificationStream(), properties.getNotificationGroup(), recordId);
    }
}
