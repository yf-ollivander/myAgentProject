package org.jeecg.modules.airag.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;

import java.util.List;

public interface IAiAgentService extends IService<AiAgent>, AuthorizedAgentConfigProvider {
    IPage<AiConfigDtos.AgentView> pageViews(String code, String name, Boolean enabled, int pageNo, int pageSize);
    AiConfigDtos.AgentView getView(String id);
    List<AiConfigDtos.AgentOption> listVisibleOptions(String keyword, int limit);
    String create(AiConfigDtos.AgentUpsertRequest request);
    void update(String id, AiConfigDtos.AgentUpsertRequest request);
    void enable(String id);
    void disable(String id);
    void delete(String id);
    AiConfigDtos.ConnectionTestResult test(String id, JsonNode input);
}
