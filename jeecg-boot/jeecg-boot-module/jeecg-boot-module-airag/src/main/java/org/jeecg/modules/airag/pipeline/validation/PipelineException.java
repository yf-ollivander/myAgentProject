package org.jeecg.modules.airag.pipeline.validation;

import lombok.Getter;

import java.util.List;

@Getter
public class PipelineException extends RuntimeException {
    private final PipelineErrorCode errorCode;
    private final List<?> details;

    public PipelineException(PipelineErrorCode errorCode, String message, List<?> details, Throwable cause) {
        super("[" + errorCode.name() + "] " + message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public static PipelineException of(PipelineErrorCode code, String message, Object detail) {
        return new PipelineException(code, message, detail == null ? List.of() : List.of(detail), null);
    }

    public static PipelineException invalid(List<ValidationIssue> issues) {
        return new PipelineException(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                "Pipeline definition validation failed", issues, null);
    }
}
