package org.jeecg.modules.airag.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ai_feishu_binding_token")
public class AiFeishuBindingToken implements Serializable {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String tenantId; private String botId; private String userId; private String username;
    private String tokenHash; private String status; private Date expiresAt;
    private String usedByOpenId; private Date usedAt; private Date createTime;
    @TableField(exist = false) private String activeTokenKey;
}
