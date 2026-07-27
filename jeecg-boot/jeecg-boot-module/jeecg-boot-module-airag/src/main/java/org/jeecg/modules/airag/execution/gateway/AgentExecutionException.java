package org.jeecg.modules.airag.execution.gateway;

import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
public class AgentExecutionException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public AgentExecutionException(String errorCode, String safeMessage, boolean retryable) {
        super(StringUtils.hasText(safeMessage) ? safeMessage : "Agent gateway execution failed");
        this.errorCode = StringUtils.hasText(errorCode) ? errorCode : "AGENT_GATEWAY_ERROR";
        this.retryable = retryable;
    }

    public static AgentExecutionException retryable(String errorCode, String safeMessage) {
        return new AgentExecutionException(errorCode, safeMessage, true);
    }

    public static AgentExecutionException nonRetryable(String errorCode, String safeMessage) {
        return new AgentExecutionException(errorCode, safeMessage, false);
    }
}
