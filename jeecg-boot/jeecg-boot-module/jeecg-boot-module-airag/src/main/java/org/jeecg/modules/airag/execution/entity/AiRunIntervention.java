package org.jeecg.modules.airag.execution.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.io.Serializable; import java.util.Date;
@Data @TableName("ai_run_intervention")
public class AiRunIntervention implements Serializable {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String tenantId; private String runId; private String nodeRunId;
    private String interventionType; private String status; private String prompt; private String allowedActionsJson;
    private String resumeToken; private String resolveRequestId; private String sourceMessageId; private String resolvedAction;
    private String resumeInputJson; private String resolvedBy; private Date resolvedAt; private Date createTime;
}
