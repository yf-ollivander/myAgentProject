package org.jeecg.modules.airag.pipeline.validation;

import lombok.Getter;

@Getter
public enum PipelineErrorCode {
    PIPELINE_NOT_FOUND_OR_FORBIDDEN(404),
    PIPELINE_CODE_DUPLICATE(409),
    PIPELINE_DRAFT_CONFLICT(409),
    PIPELINE_DEFINITION_INVALID(400),
    PIPELINE_TRIGGER_KEY_CONFLICT(409),
    PIPELINE_AGENT_NOT_AVAILABLE(400),
    PIPELINE_BOT_NOT_AVAILABLE(400),
    PIPELINE_PUBLISH_CONFLICT(409),
    PIPELINE_VERSION_NOT_FOUND(404),
    PIPELINE_NOT_PUBLISHED(409),
    PIPELINE_DISABLED(409);

    private final int resultCode;

    PipelineErrorCode(int resultCode) {
        this.resultCode = resultCode;
    }
}
