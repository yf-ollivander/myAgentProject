package org.jeecg.modules.airag.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuSession;
import java.util.List;

@Mapper
public interface AiFeishuSessionMapper extends BaseMapper<AiFeishuSession> {
    @Select("SELECT * FROM ai_feishu_session WHERE tenant_id=#{tenantId} AND bot_id=#{botId} AND chat_id=#{chatId} AND thread_key=#{threadKey} AND status IN ('ACTIVE','WAITING') FOR UPDATE")
    AiFeishuSession selectActiveForUpdate(@Param("tenantId") String tenantId, @Param("botId") String botId,
                                          @Param("chatId") String chatId, @Param("threadKey") String threadKey);

    @Select("SELECT * FROM ai_feishu_session WHERE run_id=#{runId}")
    AiFeishuSession selectByRunId(@Param("runId") String runId);

    @Select("SELECT * FROM ai_feishu_session WHERE run_id=#{runId} FOR UPDATE")
    AiFeishuSession selectByRunIdForUpdate(@Param("runId") String runId);

    @Select("SELECT * FROM ai_feishu_session WHERE status IN ('ACTIVE','WAITING') ORDER BY update_time LIMIT #{limit}")
    List<AiFeishuSession> selectReconcileCandidates(@Param("limit") int limit);
}
