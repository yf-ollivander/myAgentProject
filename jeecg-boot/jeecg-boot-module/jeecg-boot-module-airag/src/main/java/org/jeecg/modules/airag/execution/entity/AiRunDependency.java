package org.jeecg.modules.airag.execution.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.io.Serializable; import java.util.Date;
@Data @TableName("ai_run_dependency")
public class AiRunDependency implements Serializable {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String tenantId; private String runId;
    private String dependencyType; private String dependencyId; private Date createTime;
}
