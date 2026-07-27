package org.jeecg.modules.airag.execution.controller;
import org.jeecg.common.api.vo.Result;import org.jeecg.modules.airag.execution.service.ExecutionException;import org.springframework.core.*;import org.springframework.core.annotation.Order;import org.springframework.web.bind.annotation.*;
@Order(Ordered.HIGHEST_PRECEDENCE) @RestControllerAdvice(assignableTypes=AiRunController.class)
public class ExecutionExceptionHandler {@ExceptionHandler(ExecutionException.class)public Result<Object> handle(ExecutionException e){return Result.error(e.getErrorCode().getResultCode(),e.getMessage());}}
