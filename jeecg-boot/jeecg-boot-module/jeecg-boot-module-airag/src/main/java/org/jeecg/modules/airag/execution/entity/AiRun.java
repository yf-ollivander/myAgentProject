package org.jeecg.modules.airag.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.airag.agent.entity.AbstractAiConfigEntity;
import java.util.Date;

@Data @EqualsAndHashCode(callSuper = true) @TableName("ai_run")
public class AiRun extends AbstractAiConfigEntity {
    private String requestId; private String requestPayloadHash; private String runType; private String source;
    private String sourceEventId; private String sourceBotId; private String sourceChatId; private String sourceThreadId;
    private String initiatorUsername; private String pipelineId; private String pipelineVersionId; private Integer pipelineVersion;
    private String definitionHash; private String definitionJson; private String inputJson; private String workspaceContextJson;
    private String status; private String retryOfRunId; private String rootRunId; private String cancelRequestId;
    private String cancelReason; private Long eventSequence; private Date startedAt; private Date endedAt;
    @Version private Long version;
}
