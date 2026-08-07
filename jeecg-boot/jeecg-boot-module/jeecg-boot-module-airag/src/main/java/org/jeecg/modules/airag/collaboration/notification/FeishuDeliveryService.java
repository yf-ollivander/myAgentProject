package org.jeecg.modules.airag.collaboration.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.*;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuDelivery;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuDeliveryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Objects;

@Service
public class FeishuDeliveryService {
    private final AiFeishuDeliveryMapper mapper;
    private final SecretCipherService cipher;
    private final ObjectMapper objectMapper;

    public FeishuDeliveryService(AiFeishuDeliveryMapper mapper, SecretCipherService cipher, ObjectMapper objectMapper) {
        this.mapper = mapper; this.cipher = cipher; this.objectMapper = objectMapper;
    }

    public AiFeishuDelivery enqueue(String eventId, String tenantId, String runId, String eventType,
                                    Long sequence, String interventionId, String botId,
                                    DeliveryTargetType targetType, String targetId,
                                    DeliveryMessageType messageType, JsonNode content) {
        try {
            String json = objectMapper.writeValueAsString(content);
            AiFeishuDelivery delivery = new AiFeishuDelivery();
            delivery.setTenantId(tenantId); delivery.setEventId(eventId); delivery.setRunId(runId);
            delivery.setEventType(eventType); delivery.setEventSequence(sequence); delivery.setInterventionId(interventionId);
            delivery.setBotId(botId); delivery.setTargetType(targetType.name()); delivery.setTargetId(targetId);
            delivery.setMessageType(messageType.name()); delivery.setContentCipher(cipher.encrypt(json));
            delivery.setContentHash(hash(json)); delivery.setStatus(DeliveryStatus.PENDING.name());
            delivery.setRetryCount(0); delivery.setNextRetryAt(new Date()); delivery.setCreateTime(new Date());
            try { mapper.insert(delivery); return delivery; }
            catch (DuplicateKeyException duplicate) {
                AiFeishuDelivery existing = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiFeishuDelivery>()
                        .lambda().eq(AiFeishuDelivery::getEventId, eventId));
                if (existing == null || !sameDelivery(existing, delivery)) {
                    // The event ID is the cross-system idempotency key; different content is never silently merged.
                    throw new IllegalStateException("Feishu delivery event payload conflicts with an existing event");
                }
                return existing;
            }
        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Feishu delivery cannot be serialized", e); }
    }

    public AiFeishuDelivery skip(String eventId, String tenantId, String runId, String eventType,
                                 Long sequence, String interventionId, String botId, String targetId) {
        JsonNode content = objectMapper.createObjectNode().put("text", "Skipped resolved intervention notification");
        AiFeishuDelivery delivery = enqueue(eventId, tenantId, runId, eventType, sequence, interventionId, botId,
                DeliveryTargetType.REPLY, targetId, DeliveryMessageType.TEXT, content);
        if (delivery != null && DeliveryStatus.PENDING.name().equals(delivery.getStatus())) {
            delivery.setStatus(DeliveryStatus.SKIPPED.name()); mapper.updateById(delivery);
        }
        return delivery;
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(64); for (byte b : digest) result.append(String.format("%02x", b));
        return result.toString();
    }

    private boolean sameDelivery(AiFeishuDelivery left, AiFeishuDelivery right) {
        return Objects.equals(left.getTenantId(), right.getTenantId())
                && Objects.equals(left.getRunId(), right.getRunId())
                && Objects.equals(left.getEventType(), right.getEventType())
                && Objects.equals(left.getEventSequence(), right.getEventSequence())
                && Objects.equals(left.getInterventionId(), right.getInterventionId())
                && Objects.equals(left.getBotId(), right.getBotId())
                && Objects.equals(left.getTargetType(), right.getTargetType())
                && Objects.equals(left.getTargetId(), right.getTargetId())
                && Objects.equals(left.getMessageType(), right.getMessageType())
                && Objects.equals(left.getContentHash(), right.getContentHash());
    }
}
