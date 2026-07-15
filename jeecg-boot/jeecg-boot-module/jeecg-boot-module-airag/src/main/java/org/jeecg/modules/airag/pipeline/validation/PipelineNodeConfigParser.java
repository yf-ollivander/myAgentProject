package org.jeecg.modules.airag.pipeline.validation;

import org.jeecg.modules.airag.pipeline.contract.*;
import org.springframework.stereotype.Component;

@Component
public class PipelineNodeConfigParser {
    private final PipelineDefinitionCodec codec;

    public PipelineNodeConfigParser(PipelineDefinitionCodec codec) {
        this.codec = codec;
    }

    public Object parse(PipelineNode node) {
        if (node == null || node.getType() == null) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                    "Node type is required", node == null ? null : node.getId());
        }
        return switch (node.getType()) {
            case START -> codec.parseNodeConfig(node, StartNodeConfig.class);
            case AGENT -> codec.parseNodeConfig(node, AgentNodeConfig.class);
            case CONDITION -> codec.parseNodeConfig(node, ConditionNodeConfig.class);
            case NOTIFY -> codec.parseNodeConfig(node, NotifyNodeConfig.class);
            case END -> codec.parseNodeConfig(node, EndNodeConfig.class);
        };
    }

    public <T> T parse(PipelineNode node, Class<T> type) {
        return codec.parseNodeConfig(node, type);
    }
}
