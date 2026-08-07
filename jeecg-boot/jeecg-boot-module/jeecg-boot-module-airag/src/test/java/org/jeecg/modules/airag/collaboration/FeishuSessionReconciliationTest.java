package org.jeecg.modules.airag.collaboration;

import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuSession;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuUserBinding;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuDeliveryMapper;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuSessionMapper;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuUserBindingMapper;
import org.jeecg.modules.airag.collaboration.session.FeishuSessionReconciliationScheduler;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.RunStatus;
import org.jeecg.modules.airag.execution.service.RunNotificationViewProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeishuSessionReconciliationTest {
    @Test
    void closesTerminalProjectionAndSkipsResolvedInterventionCards() {
        AiFeishuSessionMapper sessions = mock(AiFeishuSessionMapper.class);
        AiFeishuUserBindingMapper bindings = mock(AiFeishuUserBindingMapper.class);
        AiFeishuDeliveryMapper deliveries = mock(AiFeishuDeliveryMapper.class);
        RunNotificationViewProvider runs = mock(RunNotificationViewProvider.class);
        AiFeishuSession session = new AiFeishuSession();
        session.setId("session-1");
        session.setTenantId("0");
        session.setRunId("run-1");
        session.setBindingId("binding-1");
        session.setStatus("WAITING");
        AiFeishuUserBinding binding = new AiFeishuUserBinding();
        binding.setUsername("alice");
        binding.setTenantId("0");
        when(sessions.selectReconcileCandidates(100)).thenReturn(List.of(session));
        when(bindings.selectById("binding-1")).thenReturn(binding);
        when(runs.state(eq("run-1"), any())).thenReturn(
                new RunNotificationViewProvider.RunCollaborationState(RunStatus.SUCCESS, null));

        new FeishuSessionReconciliationScheduler(sessions, bindings, deliveries, runs,
                new CollaborationProperties()).reconcile();

        verify(deliveries).skipStaleInterventionCards("0", "run-1", null);
        verify(sessions).updateById(session);
        assertEquals("TERMINAL", session.getStatus());
    }
}
