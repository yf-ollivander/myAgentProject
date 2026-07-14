package org.jeecg.modules.airag.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiConnector;

public interface IAiConnectorService extends IService<AiConnector> {
    IPage<AiConfigDtos.ConnectorView> pageViews(String code, String name, Boolean enabled, int pageNo, int pageSize);
    AiConfigDtos.ConnectorView getView(String id);
    AiConnector getVisibleEntity(String id);
    String create(AiConfigDtos.ConnectorUpsertRequest request);
    void update(String id, AiConfigDtos.ConnectorUpsertRequest request);
    void enable(String id);
    void disable(String id);
    void delete(String id);
    AiConfigDtos.ConnectionTestResult test(String id, JsonNode input);
}
