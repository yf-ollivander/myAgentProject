package org.jeecg.modules.airag.collaboration.inbox;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.support.FeishuInboundMessage;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.*;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.FeishuInboundCardAction;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuInboundEvent;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuInboundEventMapper;
import org.jeecg.modules.airag.collaboration.service.CollaborationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.UUID;

@Service
public class FeishuInboundIngressService implements FeishuInboundEventIngress {
    private final AiFeishuInboundEventMapper eventMapper;
    private final AiFeishuBotMapper botMapper;
    private final SecretCipherService cipher;
    private final ObjectMapper canonicalMapper;

    public FeishuInboundIngressService(AiFeishuInboundEventMapper eventMapper, AiFeishuBotMapper botMapper,
                                       SecretCipherService cipher, ObjectMapper mapper) {
        this.eventMapper = eventMapper; this.botMapper = botMapper; this.cipher = cipher;
        this.canonicalMapper = mapper.copy().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    public String persistMessage(FeishuInboundMessage message) {
        require(message.getBotId(), message.getMessageId(), message.getSenderOpenId());
        return persist("MSG:" + message.getBotId() + ":" + message.getMessageId(), message.getBotId(),
                InboundEventType.MESSAGE, message.getMessageId(), message.getSenderOpenId(), message);
    }

    @Override
    public String persistCardAction(FeishuInboundCardAction action) {
        require(action.getBotId(), action.getEventId(), action.getOperatorOpenId());
        return persist("CARD:" + action.getBotId() + ":" + action.getEventId(), action.getBotId(),
                InboundEventType.CARD_ACTION, action.getMessageId(), action.getOperatorOpenId(), action);
    }

    private String persist(String eventKey, String botId, InboundEventType type, String messageId,
                           String senderOpenId, Object payload) {
        try {
            String json = canonicalMapper.writeValueAsString(payload);
            String payloadHash = hash(json);
            AiFeishuBot bot = botMapper.selectById(botId);
            if (bot == null || !Boolean.TRUE.equals(bot.getEnabled()) || !Boolean.TRUE.equals(bot.getCommandEnabled())) {
                throw CollaborationException.notFound("FEISHU_BOT_NOT_AVAILABLE", "Feishu bot is not available");
            }
            AiFeishuInboundEvent event = new AiFeishuInboundEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTenantId(StringUtils.hasText(bot.getTenantId()) ? bot.getTenantId() : "0");
            event.setEventKey(eventKey); event.setBotId(botId); event.setEventType(type.name());
            event.setMessageId(messageId); event.setSenderOpenId(senderOpenId);
            event.setPayloadCipher(cipher.encrypt(json)); event.setPayloadHash(payloadHash);
            event.setStatus(InboundStatus.PENDING.name()); event.setRetryCount(0);
            event.setNextRetryAt(new Date()); event.setCreateTime(new Date());
            try { eventMapper.insert(event); return event.getId(); }
            catch (DuplicateKeyException duplicate) {
                AiFeishuInboundEvent existing = eventMapper.selectByEventKey(eventKey);
                if (existing != null && payloadHash.equals(existing.getPayloadHash())) return existing.getId();
                throw CollaborationException.conflict("FEISHU_EVENT_PAYLOAD_CONFLICT", "Feishu event payload conflicts with an existing event");
            }
        } catch (CollaborationException e) { throw e; }
        catch (Exception e) { throw CollaborationException.badRequest("FEISHU_COMMAND_INVALID", "Feishu event payload is invalid"); }
    }

    private void require(String botId, String eventId, String sender) {
        if (!StringUtils.hasText(botId) || !StringUtils.hasText(eventId) || !StringUtils.hasText(sender)) {
            throw CollaborationException.badRequest("FEISHU_COMMAND_INVALID", "Feishu event metadata is incomplete");
        }
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(64); for (byte b : digest) result.append(String.format("%02x", b));
        return result.toString();
    }
}
