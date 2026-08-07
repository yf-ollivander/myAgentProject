package org.jeecg.modules.airag.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ai_feishu_user_binding")
public class AiFeishuUserBinding implements Serializable {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String createBy; private Date createTime; private String updateBy; private Date updateTime;
    private String sysOrgCode; private String tenantId; @TableLogic private Integer delFlag;
    private String botId; private String senderOpenId; private String userId; private String username;
    private String bindingSource; private Boolean enabled; private Date lastUsedAt;
}
