package org.jeecg.modules.airag.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ai_pipeline_version")
public class AiPipelineVersion implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String pipelineId;
    private String tenantId;
    private Integer version;
    private Long sourceDraftRevision;
    private String schemaVersion;
    private String definitionJson;
    private String uiJson;
    private String definitionHash;
    private String publishRequestId;
    private String publishedBy;
    private Date publishedAt;
}
