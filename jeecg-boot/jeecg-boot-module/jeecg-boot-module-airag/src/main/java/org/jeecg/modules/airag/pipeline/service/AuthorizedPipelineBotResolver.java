package org.jeecg.modules.airag.pipeline.service;

import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;

import java.util.List;

public interface AuthorizedPipelineBotResolver {
    List<BotOption> listVisibleOptions(String keyword, int limit);

    AiFeishuBot resolveAvailable(String botId, AgentAccessContext context);

    record BotOption(String id, String botKey, String name, String defaultChatId) {
    }
}
