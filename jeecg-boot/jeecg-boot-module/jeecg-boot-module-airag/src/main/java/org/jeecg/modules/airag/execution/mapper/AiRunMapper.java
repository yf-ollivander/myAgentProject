package org.jeecg.modules.airag.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.airag.execution.entity.AiRun;

@Mapper
public interface AiRunMapper extends BaseMapper<AiRun> {
    @Select("SELECT * FROM ai_run WHERE id=#{id} AND tenant_id=#{tenantId} AND del_flag=0 FOR UPDATE")
    AiRun selectByIdForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);

    @Update("UPDATE ai_run SET event_sequence=event_sequence+1 WHERE id=#{id} AND tenant_id=#{tenantId} AND del_flag=0")
    int incrementEventSequence(@Param("id") String id, @Param("tenantId") String tenantId);

    @Update("UPDATE ai_run SET status='CANCELED', cancel_request_id=#{requestId}, cancel_reason=#{reason}, "
            + "ended_at=NOW(3), update_time=NOW(3), version=version+1 WHERE id=#{id} AND tenant_id=#{tenantId} "
            + "AND del_flag=0 AND status IN ('CREATED','RUNNING','WAITING')")
    int conditionalCancel(@Param("id") String id, @Param("tenantId") String tenantId,
                          @Param("requestId") String requestId, @Param("reason") String reason);
}
