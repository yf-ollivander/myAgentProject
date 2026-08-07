package org.jeecg.modules.airag.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuBindingToken;

@Mapper
public interface AiFeishuBindingTokenMapper extends BaseMapper<AiFeishuBindingToken> {
    @Select("SELECT * FROM ai_feishu_binding_token WHERE bot_id=#{botId} AND tenant_id=#{tenantId} AND user_id=#{userId} AND status='ACTIVE' FOR UPDATE")
    AiFeishuBindingToken selectActiveForUpdate(@Param("botId") String botId, @Param("tenantId") String tenantId,
                                               @Param("userId") String userId);

    @Select("SELECT * FROM ai_feishu_binding_token WHERE token_hash=#{hash} FOR UPDATE")
    AiFeishuBindingToken selectByHashForUpdate(@Param("hash") String hash);
}
