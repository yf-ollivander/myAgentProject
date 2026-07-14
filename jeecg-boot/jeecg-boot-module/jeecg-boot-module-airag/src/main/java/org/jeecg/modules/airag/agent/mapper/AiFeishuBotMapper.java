package org.jeecg.modules.airag.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;

@Mapper
public interface AiFeishuBotMapper extends BaseMapper<AiFeishuBot> {
    @Select("SELECT * FROM ai_feishu_bot WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    AiFeishuBot selectByIdForUpdate(@Param("id") String id);
}
