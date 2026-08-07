package org.jeecg.modules.airag.collaboration.service;

import lombok.Getter;

@Getter
public class CollaborationException extends RuntimeException {
    private final String errorCode;
    private final int httpCode;

    public CollaborationException(String errorCode, String message, int httpCode) {
        super(message); this.errorCode = errorCode; this.httpCode = httpCode;
    }

    public static CollaborationException notFound(String code, String message) {
        return new CollaborationException(code, message, 404);
    }
    public static CollaborationException badRequest(String code, String message) {
        return new CollaborationException(code, message, 400);
    }
    public static CollaborationException conflict(String code, String message) {
        return new CollaborationException(code, message, 409);
    }
}
