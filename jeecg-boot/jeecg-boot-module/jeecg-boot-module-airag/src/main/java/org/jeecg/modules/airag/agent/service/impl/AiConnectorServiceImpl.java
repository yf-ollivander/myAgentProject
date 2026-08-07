package org.jeecg.modules.airag.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.mapper.AiAgentMapper;
import org.jeecg.modules.airag.agent.mapper.AiConnectorMapper;
import org.jeecg.modules.airag.agent.service.AgentConnectorInvoker;
import org.jeecg.modules.airag.agent.service.IAiConnectorService;
import org.jeecg.modules.airag.agent.support.ConnectorUriPolicy;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiConnectorServiceImpl extends ServiceImpl<AiConnectorMapper, AiConnector> implements IAiConnectorService {
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Set<String> PROTECTED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "host", "content-length", "transfer-encoding");
    private final ObjectMapper objectMapper;
    private final SecretCipherService secretCipherService;
    private final ConnectorUriPolicy uriPolicy;
    private final AgentConnectorInvoker connectorInvoker;
    private final AiAgentMapper agentMapper;

    public AiConnectorServiceImpl(ObjectMapper objectMapper, SecretCipherService secretCipherService,
                                  ConnectorUriPolicy uriPolicy, AgentConnectorInvoker connectorInvoker,
                                  AiAgentMapper agentMapper) {
        this.objectMapper = objectMapper;
        this.secretCipherService = secretCipherService;
        this.uriPolicy = uriPolicy;
        this.connectorInvoker = connectorInvoker;
        this.agentMapper = agentMapper;
    }

    @Override
    public IPage<AiConfigDtos.ConnectorView> pageViews(String code, String name, Boolean enabled, int pageNo, int pageSize) {
        QueryWrapper<AiConnector> query = new QueryWrapper<>();
        query.lambda()
                .like(StringUtils.hasText(code), AiConnector::getConnectorCode, code)
                .like(StringUtils.hasText(name), AiConnector::getName, name)
                .eq(enabled != null, AiConnector::getEnabled, enabled)
                .orderByDesc(AiConnector::getCreateTime);
        QueryGenerator.installAuthMplus(query, AiConnector.class);
        Page<AiConnector> page = page(new Page<>(pageNo, pageSize), query);
        Page<AiConfigDtos.ConnectorView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toView).toList());
        return result;
    }

    @Override
    public AiConfigDtos.ConnectorView getView(String id) {
        return toView(getVisibleEntity(id));
    }

    @Override
    public AiConnector getVisibleEntity(String id) {
        QueryWrapper<AiConnector> query = new QueryWrapper<>();
        query.lambda().eq(AiConnector::getId, id);
        QueryGenerator.installAuthMplus(query, AiConnector.class);
        AiConnector entity = getOne(query, false);
        if (entity == null) {
            throw new JeecgBootException("HTTP Connector does not exist or is outside the current data scope");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(AiConfigDtos.ConnectorUpsertRequest request) {
        ensureCodeAvailable(request.getConnectorCode(), null);
        AiConnector entity = new AiConnector();
        entity.setConnectorCode(request.getConnectorCode());
        entity.setEnabled(false);
        entity.setDelFlag(0);
        entity.setLastTestStatus("UNTESTED");
        apply(entity, request);
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, AiConfigDtos.ConnectorUpsertRequest request) {
        AiConnector entity = getVisibleEntity(id);
        if (!entity.getConnectorCode().equals(request.getConnectorCode())) {
            throw new JeecgBootException("connectorCode is immutable");
        }
        apply(entity, request);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            validateEnabled(entity);
        }
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        AiConnector entity = getVisibleEntity(id);
        validateEnabled(entity);
        entity.setEnabled(true);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        AiConnector entity = getVisibleEntity(id);
        long references = agentMapper.selectCount(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getConnectorId, id).eq(AiAgent::getEnabled, true));
        if (references > 0) {
            throw new JeecgBootException("Disable referencing Agents before disabling this Connector");
        }
        entity.setEnabled(false);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AiConnector entity = getVisibleEntity(id);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            throw new JeecgBootException("Disable the Connector before deleting it");
        }
        if (agentMapper.selectCount(new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getConnectorId, id)) > 0) {
            throw new JeecgBootException("Connector is referenced by an Agent");
        }
        removeById(entity.getId());
    }

    @Override
    public AiConfigDtos.ConnectionTestResult test(String id, JsonNode input) {
        AiConnector entity = getVisibleEntity(id);
        validateEnabled(entity);
        AiConfigDtos.ConnectionTestResult result = connectorInvoker.test(entity, input);
        persistTestResult(entity, result);
        return result;
    }

    private void apply(AiConnector entity, AiConfigDtos.ConnectorUpsertRequest request) {
        uriPolicy.resolve(request.getBaseUrl(), request.getPath());
        entity.setName(request.getName());
        entity.setBaseUrl(request.getBaseUrl().trim());
        entity.setPath(request.getPath().trim());
        entity.setAuthType(request.getAuthType());
        entity.setAuthHeader("API_KEY".equals(request.getAuthType())
                ? (StringUtils.hasText(request.getAuthHeader()) ? request.getAuthHeader().trim() : "X-API-Key") : null);
        validateHeaders(request.getRequestHeaders(), entity.getAuthHeader());
        boolean legacy = AiConfigDtos.CONTRACT_LEGACY.equals(request.getResultContractVersion());
        if (legacy) validateMapping(request.getResponseMapping());
        try {
            entity.setRequestHeaders(objectMapper.writeValueAsString(
                    request.getRequestHeaders() == null ? new LinkedHashMap<>() : request.getRequestHeaders()));
            // Result 1.1 is parsed strictly and must never inherit legacy JSON-pointer mapping behavior.
            entity.setResponseMapping(legacy ? objectMapper.writeValueAsString(request.getResponseMapping()) : null);
        } catch (Exception e) {
            throw new JeecgBootException("Connector headers or response mapping are invalid", e);
        }
        entity.setConnectTimeout(request.getConnectTimeout());
        entity.setReadTimeout(request.getReadTimeout());
        entity.setResultContractVersion(request.getResultContractVersion());
        // Blank values retain the existing secret so normal edits cannot erase credentials accidentally.
        if (request.isClearSecret()) {
            entity.setSecretCipher(null);
        } else if (StringUtils.hasText(request.getSecret())) {
            entity.setSecretCipher(secretCipherService.encrypt(request.getSecret()));
        }
        if ("NONE".equals(entity.getAuthType())) {
            entity.setSecretCipher(null);
        }
    }

    private void validateEnabled(AiConnector entity) {
        uriPolicy.resolve(entity.getBaseUrl(), entity.getPath());
        if (!"NONE".equals(entity.getAuthType()) && !StringUtils.hasText(entity.getSecretCipher())) {
            throw new JeecgBootException("Connector secret is required for " + entity.getAuthType());
        }
        if ("API_KEY".equals(entity.getAuthType()) && !StringUtils.hasText(entity.getAuthHeader())) {
            throw new JeecgBootException("API Key header name is required");
        }
        if (!"NONE".equals(entity.getAuthType()) && !secretCipherService.isConfigured()) {
            throw new JeecgBootException("AI_CONFIG_SECRET_KEY is not configured");
        }
    }

    private void validateHeaders(Map<String, String> headers, String authHeader) {
        if (headers == null) {
            return;
        }
        if (headers.size() > 20) {
            throw new JeecgBootException("At most 20 custom headers are allowed");
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (!StringUtils.hasText(name) || !HEADER_NAME.matcher(name).matches() || entry.getValue() == null
                    || entry.getValue().length() > 2000 || entry.getValue().contains("\r") || entry.getValue().contains("\n")) {
                throw new JeecgBootException("Connector contains an invalid custom header");
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            if (PROTECTED_HEADERS.contains(normalized)
                    || (StringUtils.hasText(authHeader) && normalized.equals(authHeader.toLowerCase(Locale.ROOT)))) {
                throw new JeecgBootException("Authorization and transport headers must not be stored as plain custom headers");
            }
        }
    }

    private void validateMapping(AiConfigDtos.ResponseMapping mapping) {
        if (mapping == null || !mapping.getSuccessPointer().startsWith("/")
                || !mapping.getOutputPointer().startsWith("/") || !mapping.getSummaryPointer().startsWith("/")) {
            throw new JeecgBootException("Response mapping fields must be JSON Pointers beginning with '/'");
        }
        try {
            // Compile now so malformed '~' escapes cannot remain latent until a production connector call.
            JsonPointer.compile(mapping.getSuccessPointer());
            JsonPointer.compile(mapping.getOutputPointer());
            JsonPointer.compile(mapping.getSummaryPointer());
        } catch (IllegalArgumentException e) {
            throw new JeecgBootException("Response mapping contains an invalid JSON Pointer");
        }
    }

    private void ensureCodeAvailable(String code, String currentId) {
        LambdaQueryWrapper<AiConnector> query = new LambdaQueryWrapper<AiConnector>().eq(AiConnector::getConnectorCode, code)
                .ne(currentId != null, AiConnector::getId, currentId);
        if (count(query) > 0) {
            throw new JeecgBootException("connectorCode already exists");
        }
    }

    private void persistTestResult(AiConnector entity, AiConfigDtos.ConnectionTestResult result) {
        entity.setLastTestStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        entity.setLastTestMessage(abbreviate(result.getMessage()));
        entity.setLastTestTime(result.getTestedAt());
        entity.setLastTestDurationMs(result.getDurationMs());
        updateById(entity);
    }

    private AiConfigDtos.ConnectorView toView(AiConnector entity) {
        AiConfigDtos.ConnectorView view = new AiConfigDtos.ConnectorView();
        view.setId(entity.getId());
        view.setConnectorCode(entity.getConnectorCode());
        view.setName(entity.getName());
        view.setBaseUrl(entity.getBaseUrl());
        view.setPath(entity.getPath());
        view.setAuthType(entity.getAuthType());
        view.setAuthHeader(entity.getAuthHeader());
        view.setResultContractVersion(StringUtils.hasText(entity.getResultContractVersion())
                ? entity.getResultContractVersion() : AiConfigDtos.CONTRACT_LEGACY);
        try {
            view.setRequestHeaders(objectMapper.readValue(entity.getRequestHeaders(), Map.class));
            view.setResponseMapping(AiConfigDtos.CONTRACT_LEGACY.equals(view.getResultContractVersion())
                    ? objectMapper.readValue(entity.getResponseMapping(), AiConfigDtos.ResponseMapping.class) : null);
        } catch (Exception e) {
            view.setRequestHeaders(new LinkedHashMap<>());
            view.setResponseMapping(new AiConfigDtos.ResponseMapping());
        }
        view.setSecretConfigured(StringUtils.hasText(entity.getSecretCipher()));
        view.setConnectTimeout(entity.getConnectTimeout());
        view.setReadTimeout(entity.getReadTimeout());
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
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }
}
