package org.jeecg.modules.airag.execution.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.io.Serializable; import java.util.Date;
@Data @TableName("ai_artifact")
public class AiArtifact implements Serializable {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String tenantId; private String runId; private String nodeRunId;
    private String artifactType; private String name; private String uri; private String contentJson; private String checksum;
    private String artifactVersion; private String metadataJson; private Long sizeBytes; private Date createTime;
}
