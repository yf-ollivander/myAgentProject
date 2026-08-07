package org.jeecg.modules.airag.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiAgentMapper;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.service.IAiFeishuBotService;
import org.jeecg.modules.airag.agent.support.FeishuBotClient;
import org.jeecg.modules.airag.agent.support.FeishuLongConnectionManager;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiFeishuBotServiceImpl extends ServiceImpl<AiFeishuBotMapper, AiFeishuBot> implements IAiFeishuBotService {
    private final SecretCipherService secretCipherService;
    private final FeishuBotClient feishuBotClient;
    private final AiAgentMapper agentMapper;
    private final AiAgentProperties properties;
    // The long-connection manager depends on inbound command processing, which can resolve Agents.
    // Keeping this edge lazy prevents the configuration services from forming a boot-time bean cycle.
    private final FeishuLongConnectionManager longConnectionManager;

    public AiFeishuBotServiceImpl(SecretCipherService secretCipherService, FeishuBotClient feishuBotClient,
                                  AiAgentMapper agentMapper, AiAgentProperties properties,
                                  @Lazy FeishuLongConnectionManager longConnectionManager) {
        this.secretCipherService = secretCipherService;
        this.feishuBotClient = feishuBotClient;
        this.agentMapper = agentMapper;
        this.properties = properties;
        this.longConnectionManager = longConnectionManager;
    }

    @Override
    public IPage<AiConfigDtos.FeishuBotView> pageViews(String botKey, String name, Boolean enabled, String entryMode,
                                                       int pageNo, int pageSize) {
        QueryWrapper<AiFeishuBot> query = new QueryWrapper<>();
        query.lambda()
                .like(StringUtils.hasText(botKey), AiFeishuBot::getBotKey, botKey)
                .like(StringUtils.hasText(name), AiFeishuBot::getName, name)
                .eq(enabled != null, AiFeishuBot::getEnabled, enabled)
                .eq(StringUtils.hasText(entryMode), AiFeishuBot::getEntryMode, entryMode)
                .orderByDesc(AiFeishuBot::getCreateTime);
        QueryGenerator.installAuthMplus(query, AiFeishuBot.class);
        Page<AiFeishuBot> page = page(new Page<>(pageNo, pageSize), query);
        Page<AiConfigDtos.FeishuBotView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toView).toList());
        return result;
    }

    @Override
    public AiConfigDtos.FeishuBotView getView(String id) {
        return toView(getVisibleEntity(id));
    }

    @Override
    public AiFeishuBot getVisibleEntity(String id) {
        QueryWrapper<AiFeishuBot> query = new QueryWrapper<>();
        query.lambda().eq(AiFeishuBot::getId, id);
        QueryGenerator.installAuthMplus(query, AiFeishuBot.class);
        AiFeishuBot entity = getOne(query, false);
        if (entity == null) {
            throw new JeecgBootException("Feishu bot does not exist or is outside the current data scope");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(AiConfigDtos.FeishuBotUpsertRequest request) {
        if (count(new LambdaQueryWrapper<AiFeishuBot>().eq(AiFeishuBot::getBotKey, request.getBotKey())) > 0) {
            throw new JeecgBootException("botKey already exists");
        }
        ensureUniqueAppId(request.getAppId(), null);
        AiFeishuBot entity = new AiFeishuBot();
        entity.setBotKey(request.getBotKey());
        entity.setEnabled(false);
        entity.setDelFlag(0);
        entity.setLastTestStatus("UNTESTED");
        apply(entity, request);
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, AiConfigDtos.FeishuBotUpsertRequest request) {
        getVisibleEntity(id);
        // Mode changes share this row lock with Agent binding so neither side can observe stale mode state.
        AiFeishuBot entity = baseMapper.selectByIdForUpdate(id);
        if (!entity.getBotKey().equals(request.getBotKey())) {
            throw new JeecgBootException("botKey is immutable");
        }
        ensureUniqueAppId(request.getAppId(), id);
        if (AiConfigDtos.ORCHESTRATOR.equals(request.getEntryMode())
                && agentMapper.selectCount(new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getFeishuBotId, id)) > 0) {
            throw new JeecgBootException("Remove all Agent references before switching the bot to ORCHESTRATOR");
        }
        apply(entity, request);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            validateEnabled(entity);
        }
        updateById(entity);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            afterCommit(() -> longConnectionManager.start(entity));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        AiFeishuBot entity = getVisibleEntity(id);
        validateEnabled(entity);
        entity.setEnabled(true);
        updateById(entity);
        afterCommit(() -> longConnectionManager.start(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        AiFeishuBot entity = getVisibleEntity(id);
        if (agentMapper.selectCount(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getFeishuBotId, id).eq(AiAgent::getEnabled, true)) > 0) {
            throw new JeecgBootException("Disable the bound Agent before disabling this Feishu bot");
        }
        entity.setEnabled(false);
        updateById(entity);
        afterCommit(() -> longConnectionManager.stop(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AiFeishuBot entity = getVisibleEntity(id);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            throw new JeecgBootException("Disable the Feishu bot before deleting it");
        }
        if (agentMapper.selectCount(new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getFeishuBotId, id)) > 0) {
            throw new JeecgBootException("Feishu bot is referenced by an Agent");
        }
        removeById(entity.getId());
        afterCommit(() -> longConnectionManager.stop(id));
    }

    @Override
    public AiConfigDtos.ConnectionTestResult test(String id, String testMessage) {
        AiFeishuBot entity = getVisibleEntity(id);
        validateEnabled(entity);
        if (!StringUtils.hasText(entity.getDefaultChatId())) {
            throw new JeecgBootException("Default Feishu chat is required for a proactive test message");
        }
        AiConfigDtos.ConnectionTestResult result = feishuBotClient.test(entity, testMessage);
        entity.setLastTestStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        entity.setLastTestMessage(abbreviate(result.getMessage()));
        entity.setLastTestTime(result.getTestedAt());
        entity.setLastTestDurationMs(result.getDurationMs());
        updateById(entity);
        return result;
    }

    private void apply(AiFeishuBot entity, AiConfigDtos.FeishuBotUpsertRequest request) {
        entity.setName(request.getName());
        entity.setAppId(request.getAppId());
        entity.setDefaultChatId(request.getDefaultChatId());
        entity.setEntryMode(request.getEntryMode());
        entity.setCommandEnabled(Boolean.TRUE.equals(request.getCommandEnabled()));
        // Each clear flag is explicit because blank form values mean "keep the configured credential".
        entity.setAppSecretCipher(updateSecret(entity.getAppSecretCipher(), request.getAppSecret(), request.isClearAppSecret()));
        entity.setVerificationTokenCipher(updateSecret(entity.getVerificationTokenCipher(), request.getVerificationToken(), request.isClearVerificationToken()));
        entity.setEncryptKeyCipher(updateSecret(entity.getEncryptKeyCipher(), request.getEncryptKey(), request.isClearEncryptKey()));
    }

    private String updateSecret(String current, String replacement, boolean clear) {
        if (clear) {
            return null;
        }
        return StringUtils.hasText(replacement) ? secretCipherService.encrypt(replacement) : current;
    }

    private void validateEnabled(AiFeishuBot entity) {
        // SDK long connections authenticate only with App ID and App Secret.
        if (!StringUtils.hasText(entity.getAppId()) || !StringUtils.hasText(entity.getAppSecretCipher())) {
            throw new JeecgBootException("Feishu App ID and App Secret are required");
        }
        if (!secretCipherService.isConfigured()) {
            throw new JeecgBootException("AI_CONFIG_SECRET_KEY is not configured");
        }
    }

    private AiConfigDtos.FeishuBotView toView(AiFeishuBot entity) {
        AiConfigDtos.FeishuBotView view = new AiConfigDtos.FeishuBotView();
        view.setId(entity.getId());
        view.setBotKey(entity.getBotKey());
        view.setName(entity.getName());
        view.setAppId(entity.getAppId());
        view.setAppSecretConfigured(StringUtils.hasText(entity.getAppSecretCipher()));
        view.setVerificationTokenConfigured(StringUtils.hasText(entity.getVerificationTokenCipher()));
        view.setEncryptKeyConfigured(StringUtils.hasText(entity.getEncryptKeyCipher()));
        view.setDefaultChatId(entity.getDefaultChatId());
        view.setEntryMode(entity.getEntryMode());
        view.setCommandEnabled(entity.getCommandEnabled());
        String base = StringUtils.hasText(properties.getCallbackBaseUrl())
                ? properties.getCallbackBaseUrl().replaceAll("/+$", "") : "";
        view.setCallbackUrl(base + "/api/ai/callbacks/feishu/" + entity.getBotKey());
        view.setConnectionMode(FeishuLongConnectionManager.CONNECTION_MODE);
        view.setConnectionStatus(Boolean.TRUE.equals(entity.getEnabled())
                ? longConnectionManager.getStatus(entity.getId()) : "DISABLED");
        view.setEventHandlingStatus(Boolean.TRUE.equals(entity.getCommandEnabled()) ? "PROCESSING_ENABLED" : "RECEIVE_ONLY");
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

    private void ensureUniqueAppId(String appId, String excludedId) {
        LambdaQueryWrapper<AiFeishuBot> query = new LambdaQueryWrapper<AiFeishuBot>()
                .eq(AiFeishuBot::getAppId, appId);
        if (StringUtils.hasText(excludedId)) {
            query.ne(AiFeishuBot::getId, excludedId);
        }
        if (count(query) > 0) {
            throw new JeecgBootException("Feishu App ID is already configured");
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
