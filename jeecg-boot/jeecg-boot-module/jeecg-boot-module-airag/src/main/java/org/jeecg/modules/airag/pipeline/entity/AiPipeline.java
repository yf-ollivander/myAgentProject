package org.jeecg.modules.airag.pipeline.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.airag.agent.entity.AbstractAiConfigEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_pipeline")
public class AiPipeline extends AbstractAiConfigEntity {
    private String pipelineCode;
    private String name;
    private String description;
    private String draftDefinitionJson;
    private String draftUiJson;
    @Version
    private Long draftRevision;
    private String notificationBotId;
    private String defaultFeishuChatId;
    private Integer latestVersion;
    private String latestVersionId;
    private Boolean enabled;
}
