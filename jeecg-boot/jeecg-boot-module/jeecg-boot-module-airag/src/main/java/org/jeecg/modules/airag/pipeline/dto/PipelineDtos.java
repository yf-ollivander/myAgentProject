package org.jeecg.modules.airag.pipeline.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
        // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】Spring Boot 4 MVC 使用 Jackson 3，公开请求边界不能直接绑定 Jackson 2 JsonNode-----------
        @NotNull
        private Object definition;
        @NotNull
        private Object ui;
        // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】Spring Boot 4 MVC 使用 Jackson 3，公开请求边界不能直接绑定 Jackson 2 JsonNode-----------
    }

    // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】响应边界使用普通 Map/List，避免 Jackson 3 将内部 Jackson 2 JsonNode 序列化为属性清单-----------
    public record DraftView(long draftRevision, Object definition, Object ui) implements Serializable { }
    // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】响应边界使用普通 Map/List，避免 Jackson 3 将内部 Jackson 2 JsonNode 序列化为属性清单-----------

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

    public record VersionView(String id, int version, String schemaVersion, Object definition,
                              Object ui, String definitionHash, String publishedBy,
                              Date publishedAt) implements Serializable { }

    public record PipelineOption(String id, String pipelineCode, String name, int version,
                                 String versionId) implements Serializable { }
}
