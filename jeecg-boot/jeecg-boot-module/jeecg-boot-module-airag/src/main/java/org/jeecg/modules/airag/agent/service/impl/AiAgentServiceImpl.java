package org.jeecg.modules.airag.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiAgentMapper;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.service.AgentConnectorInvoker;
import org.jeecg.modules.airag.agent.service.IAiAgentService;
import org.jeecg.modules.airag.agent.service.IAiConnectorService;
import org.jeecg.modules.airag.agent.service.IAiFeishuBotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiAgentServiceImpl extends ServiceImpl<AiAgentMapper, AiAgent> implements IAiAgentService {
    private final IAiConnectorService connectorService;
    private final IAiFeishuBotService feishuBotService;
    private final AiFeishuBotMapper feishuBotMapper;
    private final AgentConnectorInvoker connectorInvoker;
    private final ObjectMapper objectMapper;

    public AiAgentServiceImpl(IAiConnectorService connectorService, IAiFeishuBotService feishuBotService,
                              AiFeishuBotMapper feishuBotMapper, AgentConnectorInvoker connectorInvoker,
                              ObjectMapper objectMapper) {
        this.connectorService = connectorService;
        this.feishuBotService = feishuBotService;
        this.feishuBotMapper = feishuBotMapper;
        this.connectorInvoker = connectorInvoker;
        this.objectMapper = objectMapper;
    }

    @Override
    public IPage<AiConfigDtos.AgentView> pageViews(String code, String name, Boolean enabled, int pageNo, int pageSize) {
        QueryWrapper<AiAgent> query = new QueryWrapper<>();
        query.lambda()
                .like(StringUtils.hasText(code), AiAgent::getAgentCode, code)
                .like(StringUtils.hasText(name), AiAgent::getName, name)
                .eq(enabled != null, AiAgent::getEnabled, enabled)
                .orderByDesc(AiAgent::getCreateTime);
        QueryGenerator.installAuthMplus(query, AiAgent.class);
        Page<AiAgent> page = page(new Page<>(pageNo, pageSize), query);
        Set<String> connectorIds = page.getRecords().stream().map(AiAgent::getConnectorId)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<String> botIds = page.getRecords().stream().map(AiAgent::getFeishuBotId)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, AiConnector> connectors = connectorIds.isEmpty() ? new LinkedHashMap<>()
                : connectorService.listByIds(connectorIds).stream().collect(Collectors.toMap(AiConnector::getId, item -> item));
        Map<String, AiFeishuBot> bots = botIds.isEmpty() ? new LinkedHashMap<>()
                : feishuBotService.listByIds(botIds).stream().collect(Collectors.toMap(AiFeishuBot::getId, item -> item));
        Page<AiConfigDtos.AgentView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(agent -> toView(agent,
                connectors.get(agent.getConnectorId()), bots.get(agent.getFeishuBotId()))).toList());
        return result;
    }

    @Override
    public AiConfigDtos.AgentView getView(String id) {
        AiAgent agent = getVisibleEntity(id);
        return toView(agent, connectorService.getById(agent.getConnectorId()),
                StringUtils.hasText(agent.getFeishuBotId()) ? feishuBotService.getById(agent.getFeishuBotId()) : null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(AiConfigDtos.AgentUpsertRequest request) {
        if (count(new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getAgentCode, request.getAgentCode())) > 0) {
            throw new JeecgBootException("agentCode already exists");
        }
        connectorService.getVisibleEntity(request.getConnectorId());
        if (StringUtils.hasText(request.getFeishuBotId())) {
            feishuBotService.getVisibleEntity(request.getFeishuBotId());
        }
        AiAgent entity = new AiAgent();
        entity.setAgentCode(request.getAgentCode());
        entity.setEnabled(false);
        entity.setDelFlag(0);
        entity.setLastTestStatus("UNTESTED");
        apply(entity, request);
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, AiConfigDtos.AgentUpsertRequest request) {
        AiAgent entity = getVisibleEntity(id);
        if (!entity.getAgentCode().equals(request.getAgentCode())) {
            throw new JeecgBootException("agentCode is immutable");
        }
        connectorService.getVisibleEntity(request.getConnectorId());
        if (StringUtils.hasText(request.getFeishuBotId())) {
            feishuBotService.getVisibleEntity(request.getFeishuBotId());
        }
        apply(entity, request);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            validateForEnable(entity, true);
        }
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        AiAgent entity = getVisibleEntity(id);
        validateForEnable(entity, true);
        entity.setEnabled(true);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        AiAgent entity = getVisibleEntity(id);
        entity.setEnabled(false);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AiAgent entity = getVisibleEntity(id);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            throw new JeecgBootException("Disable the Agent before deleting it");
        }
        removeById(entity.getId());
    }

    @Override
    public AiConfigDtos.ConnectionTestResult test(String id, JsonNode input) {
        AiAgent agent = getVisibleEntity(id);
        AiConnector connector = connectorService.getVisibleEntity(agent.getConnectorId());
        if (!Boolean.TRUE.equals(connector.getEnabled())) {
            throw new JeecgBootException("Enable the Connector before testing the Agent");
        }
        long started = System.currentTimeMillis();
        AiConfigDtos.AgentExecutionResult execution = connectorInvoker.execute(agent, connector, input);
        AiConfigDtos.ConnectionTestResult result = new AiConfigDtos.ConnectionTestResult();
        result.setSuccess(execution.isSuccess());
        result.setTestedAt(new Date());
        result.setDurationMs(System.currentTimeMillis() - started);
        result.setMessage(execution.isSuccess() ? "Agent test succeeded" : execution.getErrorMessage());
        try {
            result.setOutputPreview(execution.getOutput() == null ? null : abbreviate(objectMapper.writeValueAsString(execution.getOutput())));
        } catch (Exception ignored) {
            result.setOutputPreview(null);
        }
        agent.setLastTestStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        agent.setLastTestMessage(abbreviate(result.getMessage()));
        agent.setLastTestTime(result.getTestedAt());
        agent.setLastTestDurationMs(result.getDurationMs());
        updateById(agent);
        return result;
    }

    @Override
    public AgentConfigSnapshot getEnabledSnapshot(String agentId) {
        AiAgent agent = getById(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())) {
            throw new JeecgBootException("Enabled Agent not found");
        }
        AiConnector connector = connectorService.getById(agent.getConnectorId());
        if (connector == null || !Boolean.TRUE.equals(connector.getEnabled())) {
            throw new JeecgBootException("Agent Connector is not enabled");
        }
        Map<String, String> requestHeaders;
        AiConfigDtos.ResponseMapping responseMapping;
        try {
            requestHeaders = objectMapper.readValue(connector.getRequestHeaders(), Map.class);
            responseMapping = objectMapper.readValue(connector.getResponseMapping(), AiConfigDtos.ResponseMapping.class);
        } catch (Exception e) {
            throw new JeecgBootException("Connector snapshot configuration is invalid", e);
        }
        AgentConfigSnapshot.FeishuBotSnapshot botSnapshot = null;
        if (StringUtils.hasText(agent.getFeishuBotId())) {
            AiFeishuBot bot = feishuBotService.getById(agent.getFeishuBotId());
            if (bot != null) {
                botSnapshot = AgentConfigSnapshot.FeishuBotSnapshot.builder()
                        .botId(bot.getId()).botKey(bot.getBotKey()).defaultChatId(bot.getDefaultChatId())
                        // Long connections authenticate with the application credential only.
                        .credentialsConfigured(StringUtils.hasText(bot.getAppSecretCipher())).build();
            }
        }
        return AgentConfigSnapshot.builder()
                .agentId(agent.getId()).agentCode(agent.getAgentCode()).name(agent.getName())
                .systemPrompt(agent.getSystemPrompt()).timeoutSeconds(agent.getTimeoutSeconds()).maxRetry(agent.getMaxRetry())
                .connector(AgentConfigSnapshot.ConnectorSnapshot.builder()
                        .connectorId(connector.getId()).connectorCode(connector.getConnectorCode())
                        .baseUrl(connector.getBaseUrl()).path(connector.getPath()).authType(connector.getAuthType())
                        .authHeader(connector.getAuthHeader()).requestHeaders(requestHeaders)
                        .responseMapping(responseMapping).connectTimeout(connector.getConnectTimeout())
                        .readTimeout(connector.getReadTimeout()).secretConfigured(StringUtils.hasText(connector.getSecretCipher())).build())
                .feishuBot(botSnapshot).build();
    }

    private AiAgent getVisibleEntity(String id) {
        QueryWrapper<AiAgent> query = new QueryWrapper<>();
        query.lambda().eq(AiAgent::getId, id);
        QueryGenerator.installAuthMplus(query, AiAgent.class);
        AiAgent entity = getOne(query, false);
        if (entity == null) {
            throw new JeecgBootException("Agent does not exist or is outside the current data scope");
        }
        return entity;
    }

    private void apply(AiAgent entity, AiConfigDtos.AgentUpsertRequest request) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setSystemPrompt(request.getSystemPrompt());
        entity.setConnectorId(request.getConnectorId());
        entity.setFeishuBotId(StringUtils.hasText(request.getFeishuBotId()) ? request.getFeishuBotId() : null);
        entity.setTimeoutSeconds(request.getTimeoutSeconds());
        entity.setMaxRetry(request.getMaxRetry());
    }

    private void validateForEnable(AiAgent entity, boolean lockBot) {
        AiConnector connector = connectorService.getVisibleEntity(entity.getConnectorId());
        if (!Boolean.TRUE.equals(connector.getEnabled())) {
            throw new JeecgBootException("Agent requires an enabled Connector");
        }
        if (!StringUtils.hasText(entity.getFeishuBotId())) {
            return;
        }
        // Reapply the caller's data scope before taking the lower-level row lock used for serialization.
        feishuBotService.getVisibleEntity(entity.getFeishuBotId());
        // Locking the bot row serializes competing Agent enables and closes the race around the count check.
        AiFeishuBot bot = lockBot ? feishuBotMapper.selectByIdForUpdate(entity.getFeishuBotId())
                : feishuBotService.getVisibleEntity(entity.getFeishuBotId());
        if (bot == null || !Boolean.TRUE.equals(bot.getEnabled())) {
            throw new JeecgBootException("Bound Feishu bot must be enabled");
        }
        long conflicts = count(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getFeishuBotId, entity.getFeishuBotId())
                .eq(AiAgent::getEnabled, true)
                .ne(AiAgent::getId, entity.getId()));
        if (conflicts > 0) {
            throw new JeecgBootException("Feishu bot is already bound to another enabled Agent");
        }
    }

    private AiConfigDtos.AgentView toView(AiAgent entity, AiConnector connector, AiFeishuBot bot) {
        AiConfigDtos.AgentView view = new AiConfigDtos.AgentView();
        view.setId(entity.getId());
        view.setAgentCode(entity.getAgentCode());
        view.setName(entity.getName());
        view.setDescription(entity.getDescription());
        view.setSystemPrompt(entity.getSystemPrompt());
        view.setConnectorId(entity.getConnectorId());
        view.setConnectorName(connector == null ? null : connector.getName());
        view.setFeishuBotId(entity.getFeishuBotId());
        view.setFeishuBotName(bot == null ? null : bot.getName());
        view.setTimeoutSeconds(entity.getTimeoutSeconds());
        view.setMaxRetry(entity.getMaxRetry());
        view.setEnabled(entity.getEnabled());
        view.setLastTestStatus(entity.getLastTestStatus());
        view.setLastTestMessage(entity.getLastTestMessage());
        view.setLastTestTime(entity.getLastTestTime());
        view.setLastTestDurationMs(entity.getLastTestDurationMs());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        return view;
    }

    private String abbreviate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
