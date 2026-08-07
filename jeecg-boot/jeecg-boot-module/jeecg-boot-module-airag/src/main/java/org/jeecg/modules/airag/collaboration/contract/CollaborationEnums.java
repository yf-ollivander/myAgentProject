package org.jeecg.modules.airag.collaboration.contract;

public final class CollaborationEnums {
    private CollaborationEnums() {}

    public enum BindingSource { SELF_SERVICE, ADMIN }
    public enum BindingTokenStatus { ACTIVE, USED, EXPIRED, REVOKED }
    public enum InboundEventType { MESSAGE, CARD_ACTION }
    public enum InboundStatus { PENDING, PROCESSING, PROCESSED, FAILED, DEAD }
    public enum SessionStatus { ACTIVE, WAITING, TERMINAL, CANCELED }
    public enum DeliveryStatus { PENDING, SENDING, SENT, FAILED, DEAD, SKIPPED }
    public enum DeliveryTargetType { REPLY, CHAT }
    public enum DeliveryMessageType { TEXT, POST, CARD }
    public enum CommandType { PIPELINE, AGENT, BIND, HELP, SUPPLY_INPUT, INVALID }
}
