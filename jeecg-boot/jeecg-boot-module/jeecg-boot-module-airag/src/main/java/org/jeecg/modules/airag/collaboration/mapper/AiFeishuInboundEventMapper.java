package org.jeecg.modules.airag.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuInboundEvent;
import java.util.List;

@Mapper
public interface AiFeishuInboundEventMapper extends BaseMapper<AiFeishuInboundEvent> {
    @Select("SELECT * FROM ai_feishu_inbound_event WHERE event_key=#{eventKey}")
    AiFeishuInboundEvent selectByEventKey(@Param("eventKey") String eventKey);

    @Select("SELECT id FROM ai_feishu_inbound_event WHERE ((status IN ('PENDING','FAILED') AND next_retry_at<=NOW(3)) OR (status='PROCESSING' AND claimed_until<NOW(3))) ORDER BY create_time LIMIT #{limit}")
    List<String> selectClaimCandidates(@Param("limit") int limit);

    @Update("<script>UPDATE ai_feishu_inbound_event SET status='PROCESSING',claim_token=#{token},claimed_until=DATE_ADD(NOW(3),INTERVAL #{seconds} SECOND),update_time=NOW(3) WHERE id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach> AND ((status IN ('PENDING','FAILED') AND next_retry_at&lt;=NOW(3)) OR (status='PROCESSING' AND claimed_until&lt;NOW(3)))</script>")
    int claim(@Param("ids") List<String> ids, @Param("token") String token, @Param("seconds") int seconds);

    @Update("UPDATE ai_feishu_inbound_event SET status='PROCESSING',claim_token=#{token},claimed_until=DATE_ADD(NOW(3),INTERVAL #{seconds} SECOND),update_time=NOW(3) WHERE id=#{id} AND status IN ('PENDING','FAILED') AND next_retry_at<=NOW(3)")
    int claimOne(@Param("id") String id, @Param("token") String token, @Param("seconds") int seconds);

    @Select("SELECT * FROM ai_feishu_inbound_event WHERE claim_token=#{token} AND status='PROCESSING'")
    List<AiFeishuInboundEvent> selectClaimed(@Param("token") String token);

    @Update("UPDATE ai_feishu_inbound_event SET status='PROCESSED',payload_cipher=NULL,result_type=COALESCE(#{resultType},result_type),run_id=COALESCE(#{runId},run_id),intervention_id=COALESCE(#{interventionId},intervention_id),claim_token=NULL,claimed_until=NULL,last_error=NULL,update_time=NOW(3) WHERE id=#{id} AND claim_token=#{token} AND status='PROCESSING'")
    int markProcessed(@Param("id") String id, @Param("token") String token,
                      @Param("resultType") String resultType, @Param("runId") String runId,
                      @Param("interventionId") String interventionId);

    @Update("UPDATE ai_feishu_inbound_event SET status=#{status},retry_count=#{retryCount},next_retry_at=#{nextRetryAt},last_error=#{error},claim_token=NULL,claimed_until=NULL,update_time=NOW(3) WHERE id=#{id} AND claim_token=#{token} AND status='PROCESSING'")
    int markFailed(@Param("id") String id, @Param("token") String token, @Param("status") String status,
                   @Param("retryCount") int retryCount, @Param("nextRetryAt") java.util.Date nextRetryAt,
                   @Param("error") String error);
}
