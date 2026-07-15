package org.jeecg.modules.airag.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.airag.pipeline.entity.AiPipeline;

@Mapper
public interface AiPipelineMapper extends BaseMapper<AiPipeline> {
    @Select("SELECT * FROM ai_pipeline WHERE id=#{id} AND tenant_id=#{tenantId} AND del_flag=0 FOR UPDATE")
    AiPipeline selectByIdForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);

    @Update("UPDATE ai_pipeline SET name=#{name}, draft_definition_json=#{definitionJson}, draft_ui_json=#{uiJson}, "
            + "draft_revision=draft_revision+1, update_by=#{updateBy}, update_time=NOW() "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND draft_revision=#{revision} AND del_flag=0")
    int updateDraft(@Param("id") String id, @Param("tenantId") String tenantId,
                    @Param("revision") long revision, @Param("definitionJson") String definitionJson,
                    @Param("uiJson") String uiJson, @Param("name") String name,
                    @Param("updateBy") String updateBy);

    // Publish and enable operations must not increment the draft optimistic-lock revision.
    @Update("UPDATE ai_pipeline SET latest_version=#{latestVersion}, latest_version_id=#{latestVersionId}, "
            + "notification_bot_id=#{botId}, default_feishu_chat_id=#{chatId}, update_by=#{updateBy}, update_time=NOW() "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND del_flag=0")
    int updatePublishedState(@Param("id") String id, @Param("tenantId") String tenantId,
                             @Param("latestVersion") int latestVersion, @Param("latestVersionId") String latestVersionId,
                             @Param("botId") String botId, @Param("chatId") String chatId,
                             @Param("updateBy") String updateBy);

    @Update("UPDATE ai_pipeline SET enabled=#{enabled}, update_by=#{updateBy}, update_time=NOW() "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND del_flag=0")
    int updateEnabled(@Param("id") String id, @Param("tenantId") String tenantId,
                      @Param("enabled") boolean enabled, @Param("updateBy") String updateBy);
}
