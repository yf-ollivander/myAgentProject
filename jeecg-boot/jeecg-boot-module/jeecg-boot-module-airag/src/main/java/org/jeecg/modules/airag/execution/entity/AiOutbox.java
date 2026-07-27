package org.jeecg.modules.airag.execution.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.io.Serializable; import java.util.Date;
@Data @TableName("ai_outbox")
public class AiOutbox implements Serializable {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String tenantId; private String eventId; private String destination;
    private String eventType; private String aggregateId; private String payloadJson; private String status; private Integer retryCount;
    private Date nextRetryAt; private String claimToken; private Date claimedUntil; private String lastError; private Date createTime; private Date updateTime;
}
