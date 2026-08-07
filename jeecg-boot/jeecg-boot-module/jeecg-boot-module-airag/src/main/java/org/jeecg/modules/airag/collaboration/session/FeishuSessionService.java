package org.jeecg.modules.airag.collaboration.session;

import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.SessionStatus;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuSession;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuSessionMapper;
import org.jeecg.modules.airag.collaboration.service.CollaborationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class FeishuSessionService {
    private final AiFeishuSessionMapper mapper;

    public FeishuSessionService(AiFeishuSessionMapper mapper) { this.mapper = mapper; }

    public AiFeishuSession lockActive(String tenantId, String botId, String chatId, String threadKey) {
        return mapper.selectActiveForUpdate(tenantId, botId, chatId, threadKey);
    }

    public AiFeishuSession create(String tenantId, String botId, String chatId, String threadKey,
                                  String rootMessageId, String runId, String bindingId,
                                  String senderOpenId, String messageId) {
        AiFeishuSession session = new AiFeishuSession();
        session.setTenantId(tenantId); session.setBotId(botId); session.setChatId(chatId);
        session.setThreadKey(threadKey); session.setRootMessageId(rootMessageId); session.setRunId(runId);
        session.setBindingId(bindingId); session.setSenderOpenId(senderOpenId);
        session.setStatus(SessionStatus.ACTIVE.name()); session.setLastMessageId(messageId);
        session.setCreateTime(new Date()); session.setUpdateTime(new Date());
        try { mapper.insert(session); return session; }
        catch (DuplicateKeyException duplicate) {
            // The unique active-session key is the final concurrency guard and must roll back Run creation.
            throw CollaborationException.conflict("FEISHU_SESSION_CONFLICT", "This Feishu thread already has an active run");
        }
    }

    public void updateStatus(AiFeishuSession session, SessionStatus status, String messageId) {
        session.setStatus(status.name()); session.setLastMessageId(messageId); session.setUpdateTime(new Date());
        mapper.updateById(session);
    }
}
