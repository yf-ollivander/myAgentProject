package org.jeecg.modules.airag.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ai_feishu_session")
public class AiFeishuSession implements Serializable {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String tenantId; private String botId; private String chatId; private String threadKey;
    private String rootMessageId; private String runId; private String bindingId; private String senderOpenId;
    private String status; private String lastMessageId; private Date createTime; private Date updateTime;
    @TableField(exist = false) private String activeSessionKey;
}
