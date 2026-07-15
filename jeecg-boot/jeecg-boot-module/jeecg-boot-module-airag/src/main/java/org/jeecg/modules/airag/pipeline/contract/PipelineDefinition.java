package org.jeecg.modules.airag.pipeline.contract;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PipelineDefinition implements Serializable {
    private String schemaVersion;
    private PipelineMetadata pipeline;
    private List<PipelineNode> nodes = new ArrayList<>();
    private List<PipelineEdge> edges = new ArrayList<>();

    @Data
    public static class PipelineMetadata implements Serializable {
        private String code;
        private String name;
        private List<String> triggerAliases = new ArrayList<>();
        private String notificationBotId;
        private String defaultFeishuChatId;
        private InterventionPolicy interventionPolicy;
        private String finalSummaryTemplate;
    }

    @Data
    public static class InterventionPolicy implements Serializable {
        private PipelineEnums.ErrorPolicy onAgentNeedsUser;
        private PipelineEnums.ErrorPolicy onRetriesExhausted;
        private List<PipelineEnums.InterventionAction> allowedActions = new ArrayList<>();
    }
}
