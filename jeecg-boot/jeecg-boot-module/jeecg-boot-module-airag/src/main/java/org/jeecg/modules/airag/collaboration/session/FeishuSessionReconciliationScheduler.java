package org.jeecg.modules.airag.collaboration.session;

import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.SessionStatus;
import org.jeecg.modules.airag.collaboration.entity.*;
import org.jeecg.modules.airag.collaboration.mapper.*;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.RunStatus;
import org.jeecg.modules.airag.execution.service.RunNotificationViewProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
public class FeishuSessionReconciliationScheduler {
    private final AiFeishuSessionMapper sessions;private final AiFeishuUserBindingMapper bindings;
    private final AiFeishuDeliveryMapper deliveries;
    private final RunNotificationViewProvider runs;
    private final CollaborationProperties properties;
    public FeishuSessionReconciliationScheduler(AiFeishuSessionMapper sessions,AiFeishuUserBindingMapper bindings,
            AiFeishuDeliveryMapper deliveries,RunNotificationViewProvider runs,CollaborationProperties properties){this.sessions=sessions;this.bindings=bindings;this.deliveries=deliveries;this.runs=runs;this.properties=properties;}
    @Scheduled(fixedDelay=30000)
    public void reconcile(){if(!properties.isEnabled())return;for(AiFeishuSession session:sessions.selectReconcileCandidates(100)){try{AiFeishuUserBinding binding=bindings.selectById(session.getBindingId());if(binding==null)continue;RunNotificationViewProvider.RunCollaborationState state=runs.state(session.getRunId(),new AgentAccessContext(binding.getUsername(),binding.getTenantId()));RunStatus status=state.status();String projected=status==RunStatus.WAITING?SessionStatus.WAITING.name():status==RunStatus.CANCELED?SessionStatus.CANCELED.name():status==RunStatus.SUCCESS||status==RunStatus.FAILED?SessionStatus.TERMINAL.name():SessionStatus.ACTIVE.name();String activeInterventionId=state.openIntervention()==null?null:state.openIntervention().id();deliveries.skipStaleInterventionCards(session.getTenantId(),session.getRunId(),activeInterventionId);if(!projected.equals(session.getStatus())){session.setStatus(projected);session.setUpdateTime(new Date());sessions.updateById(session);}}catch(Exception ignored){/* MySQL run state remains authoritative; a later pass repairs transient lookup failures. */}}}
}
