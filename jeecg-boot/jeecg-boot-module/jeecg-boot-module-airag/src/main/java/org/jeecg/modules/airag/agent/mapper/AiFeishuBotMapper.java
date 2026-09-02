package org.jeecg.modules.airag.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;

import java.util.List;

@Mapper
public interface AiFeishuBotMapper extends BaseMapper<AiFeishuBot> {
    @Select("SELECT * FROM ai_feishu_bot WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    AiFeishuBot selectByIdForUpdate(@Param("id") String id);

    // Codex 2026-08-10 REQ-HTTP-MODEL-20260810: SDK and scheduler threads have no request tenant context.
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM ai_feishu_bot WHERE id = #{id} AND del_flag = 0")
    AiFeishuBot selectSystemById(@Param("id") String id);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM ai_feishu_bot WHERE enabled = 1 AND del_flag = 0")
    List<AiFeishuBot> selectSystemEnabled();
}
