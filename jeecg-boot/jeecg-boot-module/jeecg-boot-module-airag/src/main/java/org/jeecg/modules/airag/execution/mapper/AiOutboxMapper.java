package org.jeecg.modules.airag.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.airag.execution.entity.AiOutbox;
import java.util.List;

@Mapper
public interface AiOutboxMapper extends BaseMapper<AiOutbox> {
    @Select("SELECT id FROM ai_outbox WHERE next_retry_at<=NOW(3) AND "
            + "(status='PENDING' OR (status='CLAIMED' AND claimed_until<NOW(3))) ORDER BY next_retry_at,id LIMIT #{limit}")
    List<String> selectClaimCandidates(@Param("limit") int limit);

    @Update({"<script>UPDATE ai_outbox SET status='CLAIMED', claim_token=#{token}, claimed_until=DATE_ADD(NOW(3), INTERVAL #{seconds} SECOND), update_time=NOW(3) ",
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ",
            "AND (status='PENDING' OR (status='CLAIMED' AND claimed_until&lt;NOW(3)))</script>"})
    int claim(@Param("ids") List<String> ids, @Param("token") String token, @Param("seconds") int seconds);

    @Select("SELECT * FROM ai_outbox WHERE claim_token=#{token} AND status='CLAIMED' ORDER BY id")
    List<AiOutbox> selectClaimed(@Param("token") String token);

    @Update("UPDATE ai_outbox SET status='SENT', claim_token=NULL, claimed_until=NULL, update_time=NOW(3) "
            + "WHERE id=#{id} AND claim_token=#{token} AND status='CLAIMED'")
    int markSent(@Param("id") String id, @Param("token") String token);

    @Update("UPDATE ai_outbox SET status=#{status}, retry_count=#{retryCount}, next_retry_at=#{nextRetryAt}, "
            + "claim_token=NULL, claimed_until=NULL, last_error=#{lastError}, update_time=NOW(3) "
            + "WHERE id=#{id} AND claim_token=#{token} AND status='CLAIMED'")
    int markFailed(@Param("id") String id, @Param("token") String token,
                   @Param("status") String status, @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") java.util.Date nextRetryAt, @Param("lastError") String lastError);
}
