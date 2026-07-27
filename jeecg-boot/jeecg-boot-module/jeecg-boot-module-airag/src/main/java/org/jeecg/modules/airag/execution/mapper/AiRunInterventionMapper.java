package org.jeecg.modules.airag.execution.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import org.apache.ibatis.annotations.*; import org.jeecg.modules.airag.execution.entity.AiRunIntervention;
@Mapper public interface AiRunInterventionMapper extends BaseMapper<AiRunIntervention> {
    @Select("SELECT * FROM ai_run_intervention WHERE id=#{id} AND tenant_id=#{tenantId} FOR UPDATE")
    AiRunIntervention selectByIdForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);
    @Select("SELECT * FROM ai_run_intervention WHERE tenant_id=#{tenantId} AND resolve_request_id=#{requestId} FOR UPDATE")
    AiRunIntervention selectByResolveRequestIdForUpdate(@Param("tenantId") String tenantId,@Param("requestId") String requestId);
    @Select("SELECT * FROM ai_run_intervention WHERE tenant_id=#{tenantId} AND source_message_id=#{sourceMessageId} FOR UPDATE")
    AiRunIntervention selectBySourceMessageIdForUpdate(@Param("tenantId") String tenantId,@Param("sourceMessageId") String sourceMessageId);
}
