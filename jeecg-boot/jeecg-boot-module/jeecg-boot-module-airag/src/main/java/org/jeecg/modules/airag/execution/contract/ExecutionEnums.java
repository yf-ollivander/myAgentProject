package org.jeecg.modules.airag.execution.contract;

public final class ExecutionEnums {
    private ExecutionEnums() {}

    public enum RunType { PIPELINE, AGENT_DIRECT }
    public enum RunSource { JEECG, FEISHU }
    public enum RunStatus { CREATED, RUNNING, WAITING, SUCCESS, FAILED, CANCELED }
    public enum NodeStatus { PENDING, RUNNING, WAITING, SUCCESS, FAILED, SKIPPED, CANCELED }
    public enum OutboxStatus { PENDING, CLAIMED, SENT, DEAD }
    public enum OutboxDestination { TASK, NOTIFICATION }
    public enum InterventionType { NEEDS_INPUT, RETRIES_EXHAUSTED }
    public enum InterventionStatus { OPEN, RESOLVED, CANCELED }
    public enum DependencyType { PIPELINE, AGENT, CONNECTOR, BOT }
    public enum InterventionAction { SUPPLY_INPUT, RETRY, CANCEL }
}
