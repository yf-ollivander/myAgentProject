package org.jeecg.modules.airag.execution.service;

import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class RunStateMachine {
    private static final Map<RunStatus, Set<RunStatus>> RUN = Map.of(
            RunStatus.CREATED, Set.of(RunStatus.RUNNING, RunStatus.CANCELED),
            RunStatus.RUNNING, Set.of(RunStatus.WAITING, RunStatus.SUCCESS, RunStatus.FAILED, RunStatus.CANCELED),
            RunStatus.WAITING, Set.of(RunStatus.RUNNING, RunStatus.FAILED, RunStatus.CANCELED));
    private static final Map<NodeStatus, Set<NodeStatus>> NODE = Map.of(
            NodeStatus.PENDING, Set.of(NodeStatus.RUNNING, NodeStatus.SKIPPED, NodeStatus.CANCELED),
            NodeStatus.RUNNING, Set.of(NodeStatus.PENDING, NodeStatus.SUCCESS, NodeStatus.WAITING, NodeStatus.FAILED, NodeStatus.CANCELED),
            NodeStatus.WAITING, Set.of(NodeStatus.PENDING, NodeStatus.CANCELED));

    public void requireRun(RunStatus from, RunStatus to) {
        if (!RUN.getOrDefault(from, Set.of()).contains(to)) throw ExecutionException.of(
                ExecutionErrorCode.RUN_ALREADY_TERMINAL, "Run transition is not allowed: " + from + " -> " + to);
    }
    public void requireNode(NodeStatus from, NodeStatus to) {
        if (!NODE.getOrDefault(from, Set.of()).contains(to)) throw ExecutionException.of(
                ExecutionErrorCode.RUN_ALREADY_TERMINAL, "Node transition is not allowed: " + from + " -> " + to);
    }
    public boolean terminal(RunStatus status) { return Set.of(RunStatus.SUCCESS, RunStatus.FAILED, RunStatus.CANCELED).contains(status); }
    public boolean terminal(NodeStatus status) { return Set.of(NodeStatus.SUCCESS, NodeStatus.FAILED, NodeStatus.SKIPPED, NodeStatus.CANCELED).contains(status); }
}
