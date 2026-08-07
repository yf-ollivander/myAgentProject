package org.jeecg.modules.airag.execution;

import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunStateMachineTest {
    private final RunStateMachine stateMachine=new RunStateMachine();
    @Test void createdCanStartOrCancel(){assertDoesNotThrow(()->stateMachine.requireRun(RunStatus.CREATED,RunStatus.RUNNING));assertDoesNotThrow(()->stateMachine.requireRun(RunStatus.CREATED,RunStatus.CANCELED));}
    @Test void terminalRunCannotAdvance(){assertThrows(ExecutionException.class,()->stateMachine.requireRun(RunStatus.SUCCESS,RunStatus.RUNNING));}
    @Test void leaseRecoveryIsTheOnlyRunningToPendingPath(){assertDoesNotThrow(()->stateMachine.requireNode(NodeStatus.RUNNING,NodeStatus.PENDING));assertThrows(ExecutionException.class,()->stateMachine.requireNode(NodeStatus.SUCCESS,NodeStatus.PENDING));}
}
