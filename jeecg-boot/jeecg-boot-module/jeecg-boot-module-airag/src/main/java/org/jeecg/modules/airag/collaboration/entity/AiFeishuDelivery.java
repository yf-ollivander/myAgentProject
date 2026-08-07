package org.jeecg.modules.airag.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ai_feishu_delivery")
public class AiFeishuDelivery implements Serializable {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String tenantId; private String eventId; private String runId; private String eventType;
    private Long eventSequence; private String interventionId; private String botId;
    private String targetType; private String targetId; private String messageType;
    private String contentCipher; private String contentHash; private String status;
    private String claimToken; private Date claimedUntil; private Integer retryCount; private Date nextRetryAt;
    private String feishuMessageId; private String lastError; private Date createTime; private Date updateTime;
}
