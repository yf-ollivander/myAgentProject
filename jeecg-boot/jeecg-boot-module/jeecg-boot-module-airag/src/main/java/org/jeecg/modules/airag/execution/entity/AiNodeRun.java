package org.jeecg.modules.airag.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data @TableName("ai_node_run")
public class AiNodeRun implements Serializable {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String tenantId; private String runId; private String nodeId; private String stageCode; private String nodeType;
    private String status; private Integer incomingCount; private Integer resolvedIncomingCount; private Integer selectedIncomingCount;
    private Integer attemptNo; private Integer retryCount; private Integer manualRetryCount; private Integer resumeGeneration;
    private Long dispatchVersion; private Integer dispatchCount; private String leaseOwner; private Date leaseUntil;
    private Date nextRetryAt; private String inputJson; private String outputJson; private String resultSummary;
    private String selectedBranch; private String errorCode; private String errorMessage; private Date startedAt; private Date endedAt;
    @Version private Long version;
}
