package org.jeecg.modules.airag.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ai_feishu_inbound_event")
public class AiFeishuInboundEvent implements Serializable {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String tenantId; private String eventKey; private String botId; private String eventType;
    private String messageId; private String senderOpenId; private String payloadCipher; private String payloadHash;
    private String status; private String claimToken; private Date claimedUntil; private Integer retryCount;
    private Date nextRetryAt; private String resultType; private String runId; private String interventionId;
    private String lastError; private Date createTime; private Date updateTime;
}
