package org.jeecg.modules.airag.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuUserBinding;

@Mapper
public interface AiFeishuUserBindingMapper extends BaseMapper<AiFeishuUserBinding> {
    @Select("SELECT * FROM ai_feishu_user_binding WHERE bot_id=#{botId} AND sender_open_id=#{openId} AND del_flag=0 FOR UPDATE")
    AiFeishuUserBinding selectSenderForUpdate(@Param("botId") String botId, @Param("openId") String openId);

    @Select("SELECT * FROM ai_feishu_user_binding WHERE bot_id=#{botId} AND tenant_id=#{tenantId} AND user_id=#{userId} AND del_flag=0 FOR UPDATE")
    AiFeishuUserBinding selectUserForUpdate(@Param("botId") String botId, @Param("tenantId") String tenantId,
                                            @Param("userId") String userId);

    @Select("SELECT * FROM ai_feishu_user_binding WHERE id=#{id} AND tenant_id=#{tenantId} AND del_flag=0 FOR UPDATE")
    AiFeishuUserBinding selectByIdForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);
}
