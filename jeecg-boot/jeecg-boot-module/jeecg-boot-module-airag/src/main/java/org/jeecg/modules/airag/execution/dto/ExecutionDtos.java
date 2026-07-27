package org.jeecg.modules.airag.execution.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;

import java.util.Date;
import java.util.List;

public final class ExecutionDtos {
    private ExecutionDtos() {}

    public abstract static class StrictRequest {
        @JsonAnySetter
        public void rejectUnknown(String field, JsonNode value) {
            // Boot may globally ignore unknown JSON fields; public run commands must never accept authority fields.
            throw new IllegalArgumentException("Unknown request field: " + field);
        }
    }

    @Data @EqualsAndHashCode(callSuper = false) @JsonIgnoreProperties(ignoreUnknown = false)
    public static class RunCreateRequest extends StrictRequest {
        @NotBlank @Size(max = 64) private String requestId;
        @NotNull private RunType runType;
        private String pipelineId;
        private String agentId;
        @NotNull private JsonNode input;
    }
    public record RunCreateResult(String runId, RunStatus status, boolean reused) {}

    @Data @EqualsAndHashCode(callSuper = false) @JsonIgnoreProperties(ignoreUnknown = false)
    public static class RunCancelRequest extends StrictRequest {
        @NotBlank @Size(max = 64) private String requestId;
        @Size(max = 500) private String reason;
    }
    @Data @EqualsAndHashCode(callSuper = false) @JsonIgnoreProperties(ignoreUnknown = false)
    public static class RunRetryRequest extends StrictRequest { @NotBlank @Size(max = 64) private String requestId; }
    @Data @EqualsAndHashCode(callSuper = false) @JsonIgnoreProperties(ignoreUnknown = false)
    public static class InterventionResolveRequest extends StrictRequest {
        @NotBlank @Size(max = 64) private String requestId;
        @NotNull private InterventionAction action;
        private JsonNode resumeInput;
        @Size(max = 128) private String sourceMessageId;
    }
    public record RunSourceContext(RunSource source, String sourceEventId, String botId,
                                   String chatId, String threadId) {
        public static RunSourceContext jeecg() { return new RunSourceContext(RunSource.JEECG, null, null, null, null); }
    }
    public record RunListItem(String runId, RunType runType, RunSource source, RunStatus status,
                              String pipelineId, String initiatorUsername, Date createTime, Date endedAt) {}
    public record NodeView(String nodeRunId, String nodeId, String stageCode, String nodeType,
                           NodeStatus status, int attemptNo, int retryCount, String summary,
                           String errorCode, String errorMessage) {}
    public record RunDetail(String runId, RunType runType, RunSource source, RunStatus status,
                            String pipelineId, Integer pipelineVersion, JsonNode input,
                            List<NodeView> nodes, Date createTime, Date startedAt, Date endedAt) {}
    public record EventView(long sequence, String eventType, String nodeRunId, String fromStatus,
                            String toStatus, String summary, String errorCode, String traceId, Date createTime) {}
    public record ArtifactView(String id, String nodeRunId, String type, String name, String uri,
                               JsonNode content, String checksum, String version, JsonNode metadata,
                               long sizeBytes, Date createTime) {}
    public record InterventionView(String id, String nodeRunId, InterventionType type, String prompt,
                                   List<InterventionAction> allowedActions, String resumeToken, Date createTime) {}
    public record StageSummary(String nodeId, String stageCode, NodeStatus status, String summary) {}
    public record RunSummary(String runId, RunStatus status, List<StageSummary> stages,
                             List<ArtifactView> artifacts, JsonNode finalOutput,
                             String finalSummary, boolean unfinished) {}
}
