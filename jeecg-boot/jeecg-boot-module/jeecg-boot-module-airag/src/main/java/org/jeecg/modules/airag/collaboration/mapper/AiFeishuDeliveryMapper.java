package org.jeecg.modules.airag.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuDelivery;
import java.util.List;

@Mapper
public interface AiFeishuDeliveryMapper extends BaseMapper<AiFeishuDelivery> {
    @Select("SELECT d.id FROM ai_feishu_delivery d WHERE ((d.status IN ('PENDING','FAILED') AND d.next_retry_at<=NOW(3)) OR (d.status='SENDING' AND d.claimed_until<NOW(3))) AND NOT EXISTS (SELECT 1 FROM ai_feishu_delivery p WHERE p.run_id=d.run_id AND p.event_sequence<d.event_sequence AND p.status IN ('PENDING','FAILED','SENDING')) ORDER BY d.create_time LIMIT #{limit}")
    List<String> selectClaimCandidates(@Param("limit") int limit);

    @Update("<script>UPDATE ai_feishu_delivery SET status='SENDING',claim_token=#{token},claimed_until=DATE_ADD(NOW(3),INTERVAL #{seconds} SECOND),update_time=NOW(3) WHERE id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach> AND ((status IN ('PENDING','FAILED') AND next_retry_at&lt;=NOW(3)) OR (status='SENDING' AND claimed_until&lt;NOW(3)))</script>")
    int claim(@Param("ids") List<String> ids, @Param("token") String token, @Param("seconds") int seconds);

    @Select("SELECT * FROM ai_feishu_delivery WHERE claim_token=#{token} AND status='SENDING' ORDER BY create_time")
    List<AiFeishuDelivery> selectClaimed(@Param("token") String token);

    @Update("UPDATE ai_feishu_delivery SET status='SENT',feishu_message_id=#{messageId},claim_token=NULL,claimed_until=NULL,last_error=NULL,update_time=NOW(3) WHERE id=#{id} AND claim_token=#{token} AND status='SENDING'")
    int markSent(@Param("id") String id, @Param("token") String token, @Param("messageId") String messageId);

    @Update("UPDATE ai_feishu_delivery SET status=#{status},retry_count=#{retryCount},next_retry_at=#{nextRetryAt},last_error=#{error},claim_token=NULL,claimed_until=NULL,update_time=NOW(3) WHERE id=#{id} AND claim_token=#{token} AND status='SENDING'")
    int markFailed(@Param("id") String id, @Param("token") String token, @Param("status") String status,
                   @Param("retryCount") int retryCount, @Param("nextRetryAt") java.util.Date nextRetryAt,
                   @Param("error") String error);

    @Update("UPDATE ai_feishu_delivery SET status='SKIPPED',claim_token=NULL,claimed_until=NULL,last_error=NULL,update_time=NOW(3) "
            + "WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND message_type='CARD' "
            + "AND status IN ('PENDING','FAILED') AND (#{activeInterventionId} IS NULL OR intervention_id IS NULL OR intervention_id <> #{activeInterventionId})")
    int skipStaleInterventionCards(@Param("tenantId") String tenantId, @Param("runId") String runId,
                                   @Param("activeInterventionId") String activeInterventionId);
}
