package org.jeecg.modules.airag.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.airag.execution.entity.AiNodeRun;
import java.util.Date;
import java.util.List;

@Mapper
public interface AiNodeRunMapper extends BaseMapper<AiNodeRun> {
    @Select("SELECT * FROM ai_node_run WHERE id=#{id} AND tenant_id=#{tenantId} FOR UPDATE")
    AiNodeRun selectByIdForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);

    @Select("SELECT * FROM ai_node_run WHERE run_id=#{runId} AND tenant_id=#{tenantId} ORDER BY id FOR UPDATE")
    List<AiNodeRun> selectRunNodesForUpdate(@Param("runId") String runId, @Param("tenantId") String tenantId);

    @Update("UPDATE ai_node_run SET status='RUNNING', lease_owner=#{owner}, lease_until=#{leaseUntil}, "
            + "dispatch_count=dispatch_count+1, started_at=COALESCE(started_at,NOW(3)), version=version+1 "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND status='PENDING' AND dispatch_version=#{dispatchVersion} "
            + "AND (next_retry_at IS NULL OR next_retry_at<=NOW(3))")
    int claimPending(@Param("id") String id, @Param("tenantId") String tenantId,
                     @Param("dispatchVersion") long dispatchVersion, @Param("owner") String owner,
                     @Param("leaseUntil") Date leaseUntil);

    @Update("UPDATE ai_node_run SET resolved_incoming_count=resolved_incoming_count+1, "
            + "selected_incoming_count=selected_incoming_count+#{selected}, version=version+1 "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND resolved_incoming_count<incoming_count")
    int resolveIncoming(@Param("id") String id, @Param("tenantId") String tenantId,
                        @Param("selected") int selected);

    @Update("UPDATE ai_node_run SET status='PENDING', lease_owner=NULL, lease_until=NULL, dispatch_version=dispatch_version+1, "
            + "version=version+1 WHERE id=#{id} AND tenant_id=#{tenantId} AND status='RUNNING' AND lease_until<#{now}")
    int recoverExpiredLease(@Param("id") String id, @Param("tenantId") String tenantId, @Param("now") Date now);
}
