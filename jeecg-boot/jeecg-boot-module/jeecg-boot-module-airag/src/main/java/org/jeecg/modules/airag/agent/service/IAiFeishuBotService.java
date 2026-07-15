package org.jeecg.modules.airag.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;

public interface IAiFeishuBotService extends IService<AiFeishuBot> {
    IPage<AiConfigDtos.FeishuBotView> pageViews(String botKey, String name, Boolean enabled, String entryMode,
                                                int pageNo, int pageSize);
    AiConfigDtos.FeishuBotView getView(String id);
    AiFeishuBot getVisibleEntity(String id);
    String create(AiConfigDtos.FeishuBotUpsertRequest request);
    void update(String id, AiConfigDtos.FeishuBotUpsertRequest request);
    void enable(String id);
    void disable(String id);
    void delete(String id);
    AiConfigDtos.ConnectionTestResult test(String id, String testMessage);
}
