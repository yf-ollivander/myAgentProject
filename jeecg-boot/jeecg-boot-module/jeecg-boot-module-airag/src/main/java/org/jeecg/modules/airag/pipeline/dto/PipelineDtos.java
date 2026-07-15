package org.jeecg.modules.airag.pipeline.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.airag.pipeline.contract.PipelineDefinition;
import org.jeecg.modules.airag.pipeline.contract.PipelineUiModel;
import org.jeecg.modules.airag.pipeline.validation.ValidationIssue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class PipelineDtos {
    private PipelineDtos() {
    }

    @Data
    public static class CreateRequest implements Serializable {
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{0,63}$")
        private String code;
        @NotBlank @Size(max = 100)
        private String name;
        @Size(max = 500)
        private String description;
    }

    public record CreateResult(String id, long draftRevision) implements Serializable { }

    @Data
    public static class DraftSaveRequest implements Serializable {
        @NotNull @Min(0)
        private Long draftRevision;
        @NotNull
        private JsonNode definition;
        @NotNull
        private JsonNode ui;
    }

    public record DraftView(long draftRevision, PipelineDefinition definition, PipelineUiModel ui) implements Serializable { }

    @Data
    public static class RevisionRequest implements Serializable {
        @NotNull @Min(0)
        private Long draftRevision;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PublishRequest extends RevisionRequest {
        @NotBlank @Size(max = 64)
        private String requestId;
    }

    public record PublishResult(String versionId, int version, String definitionHash, boolean reused) implements Serializable { }

    public record ValidationResult(boolean valid, List<ValidationIssue> errors,
                                   List<ValidationIssue> warnings) implements Serializable {
        public static ValidationResult of(List<ValidationIssue> errors) {
            return new ValidationResult(errors.isEmpty(), errors, List.of());
        }
    }

    @Data
    public static class ListItem implements Serializable {
        private String id;
        private String pipelineCode;
        private String name;
        private String description;
        private long draftRevision;
        private int latestVersion;
        private boolean enabled;
        private Date updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Detail extends ListItem {
        private String notificationBotId;
        private String defaultFeishuChatId;
        private String latestVersionId;
    }

    public record VersionSummary(String id, int version, long sourceDraftRevision, String definitionHash,
                                 String publishedBy, Date publishedAt) implements Serializable { }

    public record VersionView(String id, int version, String schemaVersion, JsonNode definition,
                              PipelineUiModel ui, String definitionHash, String publishedBy,
                              Date publishedAt) implements Serializable { }

    public record PipelineOption(String id, String pipelineCode, String name, int version,
                                 String versionId) implements Serializable { }
}
