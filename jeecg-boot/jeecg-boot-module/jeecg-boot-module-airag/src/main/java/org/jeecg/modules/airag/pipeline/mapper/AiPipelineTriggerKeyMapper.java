package org.jeecg.modules.airag.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.airag.pipeline.entity.AiPipelineTriggerKey;

@Mapper
public interface AiPipelineTriggerKeyMapper extends BaseMapper<AiPipelineTriggerKey> {
    @Delete("DELETE FROM ai_pipeline_trigger_key WHERE pipeline_id=#{pipelineId}")
    int deleteByPipelineId(@Param("pipelineId") String pipelineId);

    @Select("SELECT * FROM ai_pipeline_trigger_key WHERE tenant_id=#{tenantId} AND normalized_value=#{value} LIMIT 1")
    AiPipelineTriggerKey selectByNormalizedValue(@Param("tenantId") String tenantId, @Param("value") String value);
}
