package org.jeecg.modules.airag.collaboration.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.support.FeishuBotClient;
import org.jeecg.modules.airag.agent.support.FeishuBotClient.FeishuApiException;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.*;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuDelivery;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuDeliveryMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FeishuDeliveryDispatcher {
    private static final int[] BACKOFF={2,5,10,30,60};
    private static final String CLAIM_CANDIDATES_SQL="SELECT d.id FROM ai_feishu_delivery d WHERE ((d.status IN ('PENDING','FAILED') AND d.next_retry_at<=NOW(3)) OR (d.status='SENDING' AND d.claimed_until<NOW(3))) AND NOT EXISTS (SELECT 1 FROM ai_feishu_delivery p WHERE p.run_id=d.run_id AND p.event_sequence<d.event_sequence AND p.status IN ('PENDING','FAILED','SENDING')) ORDER BY d.create_time LIMIT ?";
    private final AiFeishuDeliveryMapper mapper;private final AiFeishuBotMapper bots;
    private final SecretCipherService cipher;private final ObjectMapper objectMapper;
    private final FeishuBotClient client;private final CollaborationProperties properties;private final JdbcTemplate jdbcTemplate;
    public FeishuDeliveryDispatcher(AiFeishuDeliveryMapper mapper,AiFeishuBotMapper bots,SecretCipherService cipher,
            ObjectMapper objectMapper,FeishuBotClient client,CollaborationProperties properties,JdbcTemplate jdbcTemplate){this.mapper=mapper;this.bots=bots;this.cipher=cipher;this.objectMapper=objectMapper;this.client=client;this.properties=properties;this.jdbcTemplate=jdbcTemplate;}
    @Scheduled(fixedDelayString="${ai.collaboration.delivery-interval-ms:500}")
    public void dispatch(){if(!properties.isEnabled())return;List<String> ids=selectClaimCandidates();if(ids.isEmpty())return;String token=UUID.randomUUID().toString();mapper.claim(ids,token,properties.getDeliveryClaimSeconds());for(AiFeishuDelivery delivery:mapper.selectClaimed(token))send(delivery,token);}
    // Keep only this empty-poll query outside MyBatis so StdOutImpl can still print useful business SQL.
    private List<String> selectClaimCandidates(){return jdbcTemplate.queryForList(CLAIM_CANDIDATES_SQL,String.class,properties.getDeliveryBatchSize());}
    // Delivery workers use the internal botId persisted with the tenant-owned Delivery row.
    private void send(AiFeishuDelivery delivery,String token){try{AiFeishuBot bot=bots.selectSystemById(delivery.getBotId());if(bot==null||!Boolean.TRUE.equals(bot.getEnabled()))throw new FeishuApiException(404,false,null,"Bot unavailable");JsonNode content=objectMapper.readTree(cipher.decrypt(delivery.getContentCipher()));FeishuBotClient.DeliveryResult result=client.deliver(bot,DeliveryTargetType.valueOf(delivery.getTargetType()),delivery.getTargetId(),DeliveryMessageType.valueOf(delivery.getMessageType()),content,delivery.getEventId());mapper.markSent(delivery.getId(),token,result.messageId());}catch(FeishuApiException e){fail(delivery,token,e.isRetryable(),e.getRetryAfterSeconds(),e.getMessage());}catch(Exception e){fail(delivery,token,true,null,"Feishu delivery failed");}}
    private void fail(AiFeishuDelivery delivery,String token,boolean retryable,Integer retryAfter,String error){int retry=delivery.getRetryCount()+1;boolean dead=!retryable||retry>=properties.getNotificationMaxRetries();int seconds=retryAfter!=null?retryAfter:BACKOFF[Math.min(retry-1,BACKOFF.length-1)];mapper.markFailed(delivery.getId(),token,dead?DeliveryStatus.DEAD.name():DeliveryStatus.FAILED.name(),retry,new Date(System.currentTimeMillis()+seconds*1000L),error==null?"FEISHU_DELIVERY_DEAD":error.substring(0,Math.min(1000,error.length())));}
}
