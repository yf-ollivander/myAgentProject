package org.jeecg.modules.airag.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ai_pipeline_trigger_key")
public class AiPipelineTriggerKey implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String tenantId;
    private String pipelineId;
    private String publishedVersionId;
    private String keyType;
    private String displayValue;
    private String normalizedValue;
    private String createBy;
    private Date createTime;
}
