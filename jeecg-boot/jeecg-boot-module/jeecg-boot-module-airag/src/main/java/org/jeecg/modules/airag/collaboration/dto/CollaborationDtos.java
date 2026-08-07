package org.jeecg.modules.airag.collaboration.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import java.util.Date;
import java.util.Map;

public final class CollaborationDtos {
    private CollaborationDtos() {}

    public abstract static class StrictRequest {
        @JsonAnySetter
        public void rejectUnknown(String field, JsonNode value) {
            // Authority fields must never be accepted through globally permissive Jackson settings.
            throw new IllegalArgumentException("Unknown request field: " + field);
        }
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class BindingCreateRequest extends StrictRequest {
        @NotBlank private String botId;
        @NotBlank @Size(max = 128) private String senderOpenId;
        @NotBlank @Size(max = 50) private String username;
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class BindingTokenRequest extends StrictRequest {
        @NotBlank private String botId;
    }

    public record BindingTokenResult(String code, Date expiresAt, String command) {}
    public record BindingView(String id, String botId, String maskedSenderOpenId, String username,
                              String source, boolean enabled, Date createTime, Date lastUsedAt) {}

    @Value
    @Builder
    public static class FeishuInboundCardAction {
        String botId;
        String eventId;
        String operatorOpenId;
        String messageId;
        String chatId;
        String token;
        String actionTag;
        Map<String, JsonNode> actionValue;
        Map<String, JsonNode> formValue;
        String inputValue;
        @com.alibaba.fastjson.annotation.JSONField(serialize = false)
        @lombok.ToString.Exclude
        JsonNode cardContent;
    }

    public record Command(String type, String key, String task) {}

    public record InboundProcessResult(String resultType, String runId, String interventionId) {
        public static InboundProcessResult processed(String type) {
            return new InboundProcessResult(type, null, null);
        }
    }

    public record BusinessNotificationEvent(String eventId, String eventType, String runId,
                                            String nodeRunId, String tenantId, String traceId,
                                            long eventSequence, String interventionId) {}

    public record NotificationView(String botId, String targetType, String targetId,
                                   String messageType, JsonNode content) {}
}
