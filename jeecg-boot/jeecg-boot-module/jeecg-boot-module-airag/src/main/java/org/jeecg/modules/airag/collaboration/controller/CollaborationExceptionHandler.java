package org.jeecg.modules.airag.collaboration.controller;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.airag.collaboration.service.CollaborationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = FeishuBindingController.class)
public class CollaborationExceptionHandler {
    @ExceptionHandler(CollaborationException.class)
    public Result<Object> handle(CollaborationException exception) {
        return Result.error(exception.getHttpCode(), exception.getMessage());
    }
}
