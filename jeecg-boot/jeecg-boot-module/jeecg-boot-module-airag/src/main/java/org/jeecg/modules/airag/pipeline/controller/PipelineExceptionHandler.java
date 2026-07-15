package org.jeecg.modules.airag.pipeline.controller;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.airag.pipeline.validation.PipelineException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AiPipelineController.class)
public class PipelineExceptionHandler {
    @ExceptionHandler(PipelineException.class)
    public Result<Object> handle(PipelineException exception) {
        Result<Object> result = Result.error(exception.getErrorCode().getResultCode(), exception.getMessage());
        result.setResult(exception.getDetails());
        return result;
    }
}
