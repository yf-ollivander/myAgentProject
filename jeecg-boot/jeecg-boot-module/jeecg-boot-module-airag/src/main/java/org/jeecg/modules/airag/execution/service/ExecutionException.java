package org.jeecg.modules.airag.execution.service;

import lombok.Getter;

@Getter
public class ExecutionException extends RuntimeException {
    private final ExecutionErrorCode errorCode;
    public ExecutionException(ExecutionErrorCode errorCode, String message) {
        super("[" + errorCode.name() + "] " + message);
        this.errorCode = errorCode;
    }
    public static ExecutionException of(ExecutionErrorCode code, String message) {
        return new ExecutionException(code, message);
    }
}
