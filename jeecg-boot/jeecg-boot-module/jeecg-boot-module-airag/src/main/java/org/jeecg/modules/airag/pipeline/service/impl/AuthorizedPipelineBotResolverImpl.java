package org.jeecg.modules.airag.pipeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.SysPermissionDataRuleModel;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineBotResolver;
import org.jeecg.modules.airag.pipeline.service.PipelineAccessContextFactory;
import org.jeecg.modules.airag.pipeline.validation.PipelineErrorCode;
import org.jeecg.modules.airag.pipeline.validation.PipelineException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class AuthorizedPipelineBotResolverImpl implements AuthorizedPipelineBotResolver {
    private static final String COMPONENT = "super/multiagent/feishu/AiFeishuBotList";
    private static final String PATH = "/api/ai/pipelines/notification-bot-options";
    private final AiFeishuBotMapper mapper;
    private final CommonAPI commonApi;
    private final PipelineAccessContextFactory contextFactory;

    public AuthorizedPipelineBotResolverImpl(AiFeishuBotMapper mapper, CommonAPI commonApi,
                                             PipelineAccessContextFactory contextFactory) {
        this.mapper = mapper;
        this.commonApi = commonApi;
        this.contextFactory = contextFactory;
    }

    @Override
    public List<BotOption> listVisibleOptions(String keyword, int limit) {
        AgentAccessContext context = contextFactory.current();
        Authorization authorization = authorization(context);
        return withTenant(context.tenantId(), () -> {
            QueryWrapper<AiFeishuBot> query = availableQuery(keyword, limit);
            QueryGenerator.installAuthMplus(query, AiFeishuBot.class, authorization.rules(),
                    authorization.user(), context.tenantId());
            return mapper.selectList(query).stream().map(this::option).toList();
        });
    }

    @Override
    public AiFeishuBot resolveAvailable(String botId, AgentAccessContext context) {
        Authorization authorization = authorization(context);
        return withTenant(context.tenantId(), () -> {
            QueryWrapper<AiFeishuBot> query = availableQuery(null, 1);
            query.lambda().eq(AiFeishuBot::getId, botId);
            QueryGenerator.installAuthMplus(query, AiFeishuBot.class, authorization.rules(),
                    authorization.user(), context.tenantId());
            AiFeishuBot bot = mapper.selectOne(query);
            if (bot == null) throw PipelineException.of(PipelineErrorCode.PIPELINE_BOT_NOT_AVAILABLE,
                    "Notification bot is unavailable or forbidden", botId);
            return bot;
        });
    }

    private QueryWrapper<AiFeishuBot> availableQuery(String keyword, int limit) {
        if (limit < 1 || limit > 200) throw PipelineException.of(PipelineErrorCode.PIPELINE_DEFINITION_INVALID,
                "Bot option limit must be between 1 and 200", limit);
        QueryWrapper<AiFeishuBot> query = new QueryWrapper<>();
        query.lambda().eq(AiFeishuBot::getEnabled, true)
                .eq(AiFeishuBot::getEntryMode, AiConfigDtos.ORCHESTRATOR)
                .eq(AiFeishuBot::getCommandEnabled, true)
                .and(StringUtils.hasText(keyword), q -> q.like(AiFeishuBot::getBotKey, keyword)
                        .or().like(AiFeishuBot::getName, keyword))
                .orderByAsc(AiFeishuBot::getName).last("LIMIT " + limit);
        return query;
    }

    private Authorization authorization(AgentAccessContext context) {
        if (context == null || !StringUtils.hasText(context.username()) || !StringUtils.hasText(context.tenantId())) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                    "Pipeline access context is incomplete", null);
        }
        SysUserCacheInfo user = commonApi.getCacheUser(context.username());
        Set<String> permissions = user == null ? Set.of() : commonApi.queryUserAuths(user.getSysUserId());
        if (user == null || permissions == null || !permissions.contains("ai:feishu:list")) {
            throw PipelineException.of(PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                    "User cannot access Feishu bot options", null);
        }
        List<SysPermissionDataRuleModel> rules = commonApi.queryPermissionDataRule(COMPONENT, PATH, context.username());
        return new Authorization(user, rules == null ? List.of() : rules);
    }

    private <T> T withTenant(String tenantId, Supplier<T> action) {
        String previous = TenantContext.getTenant();
        try {
            TenantContext.setTenant(tenantId);
            return action.get();
        } finally {
            // Async resolver threads are reused; tenant state must never leak into the next request.
            if (StringUtils.hasText(previous)) TenantContext.setTenant(previous); else TenantContext.clear();
        }
    }

    private BotOption option(AiFeishuBot bot) {
        return new BotOption(bot.getId(), bot.getBotKey(), bot.getName(), bot.getDefaultChatId());
    }

    private record Authorization(SysUserCacheInfo user, List<SysPermissionDataRuleModel> rules) {
    }
}
