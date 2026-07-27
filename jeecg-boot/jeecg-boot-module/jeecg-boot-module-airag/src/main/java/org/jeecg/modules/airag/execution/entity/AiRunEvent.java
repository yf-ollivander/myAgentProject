package org.jeecg.modules.airag.execution.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.io.Serializable; import java.util.Date;
@Data @TableName("ai_run_event")
public class AiRunEvent implements Serializable {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String tenantId; private String runId; private String nodeRunId;
    private Long sequence; private String eventType; private String fromStatus; private String toStatus; private String summary;
    private String errorCode; private String traceId; private String payloadJson; private Date createTime;
}
