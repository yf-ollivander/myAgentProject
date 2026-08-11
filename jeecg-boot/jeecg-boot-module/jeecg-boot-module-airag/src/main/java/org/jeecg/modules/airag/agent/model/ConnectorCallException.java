package org.jeecg.modules.airag.agent.model;

import lombok.Getter;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】在测试调用与正式 Gateway 间共享安全错误事实-----------
@Getter
public class ConnectorCallException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;
    private final Integer httpStatus;

    public ConnectorCallException(String errorCode, String message, boolean retryable, Integer httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public static ConnectorCallException nonRetryable(String code, String message) {
        return new ConnectorCallException(code, message, false, null);
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】在测试调用与正式 Gateway 间共享安全错误事实-----------
