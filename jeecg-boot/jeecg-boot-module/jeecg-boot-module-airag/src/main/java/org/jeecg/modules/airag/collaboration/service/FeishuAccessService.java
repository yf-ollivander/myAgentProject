package org.jeecg.modules.airag.collaboration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.system.vo.SysPermissionDataRuleModel;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class FeishuAccessService {
    private static final String COMPONENT = "super/multiagent/feishu/AiFeishuBotList";
    private static final String PATH = "/api/ai/feishu-bots";
    private final AiFeishuBotMapper botMapper;
    private final CommonAPI commonApi;

    public FeishuAccessService(AiFeishuBotMapper botMapper, CommonAPI commonApi) {
        this.botMapper = botMapper;
        this.commonApi = commonApi;
    }

    public AiFeishuBot requireBot(String botId, AgentAccessContext context, boolean requireCommand) {
        Authorization auth = authorization(context, Set.of("ai:feishu:list"));
        return withTenant(context.tenantId(), () -> {
            QueryWrapper<AiFeishuBot> query = new QueryWrapper<>();
            query.lambda().eq(AiFeishuBot::getId, botId).eq(AiFeishuBot::getEnabled, true)
                    .eq(requireCommand, AiFeishuBot::getCommandEnabled, true).eq(AiFeishuBot::getDelFlag, 0)
                    .and("0".equals(context.tenantId()), q -> q.eq(AiFeishuBot::getTenantId, "0")
                            .or().isNull(AiFeishuBot::getTenantId).or().eq(AiFeishuBot::getTenantId, ""))
                    .eq(!"0".equals(context.tenantId()), AiFeishuBot::getTenantId, context.tenantId());
            QueryGenerator.installAuthMplus(query, AiFeishuBot.class, auth.rules(), auth.user(), context.tenantId());
            AiFeishuBot bot = botMapper.selectOne(query);
            if (bot == null) throw CollaborationException.notFound("FEISHU_BOT_NOT_AVAILABLE", "Feishu bot is not available");
            return bot;
        });
    }

    public LoginUser requireUser(String username, String tenantId) {
        LoginUser user = commonApi.getUserByName(username);
        if (user == null || !StringUtils.hasText(user.getId()) || !Integer.valueOf(1).equals(user.getStatus())
                || Integer.valueOf(1).equals(user.getDelFlag()) || !tenantMember(user, tenantId)) {
            throw CollaborationException.notFound("FEISHU_USER_FORBIDDEN", "JEECG user is not available");
        }
        return user;
    }

    public void requirePermissions(AgentAccessContext context, String... permissions) {
        authorization(context, Set.of(permissions));
    }

    private Authorization authorization(AgentAccessContext context, Set<String> required) {
        if (context == null || !StringUtils.hasText(context.username()) || !StringUtils.hasText(context.tenantId())) {
            throw CollaborationException.notFound("FEISHU_USER_FORBIDDEN", "User context is not available");
        }
        SysUserCacheInfo user = commonApi.getCacheUser(context.username());
        Set<String> permissions = user == null ? Set.of() : commonApi.queryUserAuths(user.getSysUserId());
        if (user == null || permissions == null || !permissions.containsAll(required)) {
            throw CollaborationException.notFound("FEISHU_USER_FORBIDDEN", "User is not authorized");
        }
        List<SysPermissionDataRuleModel> rules = commonApi.queryPermissionDataRule(COMPONENT, PATH, context.username());
        return new Authorization(user, rules == null ? List.of() : rules);
    }

    private boolean tenantMember(LoginUser user, String tenantId) {
        if ("0".equals(tenantId)) return true;
        return StringUtils.hasText(user.getRelTenantIds())
                && Arrays.stream(user.getRelTenantIds().split(",")).map(String::trim).anyMatch(tenantId::equals);
    }

    private <T> T withTenant(String tenantId, Supplier<T> action) {
        String previous = TenantContext.getTenant();
        try { TenantContext.setTenant(tenantId); return action.get(); }
        finally { if (StringUtils.hasText(previous)) TenantContext.setTenant(previous); else TenantContext.clear(); }
    }

    private record Authorization(SysUserCacheInfo user, List<SysPermissionDataRuleModel> rules) {}
}
