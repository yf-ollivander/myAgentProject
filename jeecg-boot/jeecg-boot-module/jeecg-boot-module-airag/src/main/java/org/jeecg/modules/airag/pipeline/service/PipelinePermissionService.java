package org.jeecg.modules.airag.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.SysPermissionDataRuleModel;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.pipeline.entity.AiPipeline;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineMapper;
import org.jeecg.modules.airag.pipeline.validation.PipelineErrorCode;
import org.jeecg.modules.airag.pipeline.validation.PipelineException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class PipelinePermissionService {
    public static final String COMPONENT = "super/multiagent/pipeline/AiPipelineList";
    private static final String PATH = "/api/ai/pipelines";
    private final AiPipelineMapper mapper;
    private final CommonAPI commonApi;

    public PipelinePermissionService(AiPipelineMapper mapper, CommonAPI commonApi) {
        this.mapper = mapper;
        this.commonApi = commonApi;
    }

    public AiPipeline requireVisibleCurrent(String id) {
        QueryWrapper<AiPipeline> query = new QueryWrapper<>();
        query.lambda().eq(AiPipeline::getId, id);
        QueryGenerator.installAuthMplus(query, AiPipeline.class);
        return requireOne(mapper.selectOne(query));
    }

    public AiPipeline requireVisible(String id, AgentAccessContext context) {
        Authorization authorization = authorization(context);
        return withTenant(context.tenantId(), () -> {
            QueryWrapper<AiPipeline> query = new QueryWrapper<>();
            query.lambda().eq(AiPipeline::getId, id).eq(AiPipeline::getTenantId, context.tenantId());
            QueryGenerator.installAuthMplus(query, AiPipeline.class, authorization.rules(),
                    authorization.user(), context.tenantId());
            return requireOne(mapper.selectOne(query));
        });
    }

    public QueryWrapper<AiPipeline> authorizedQuery(AgentAccessContext context) {
        Authorization authorization = authorization(context);
        QueryWrapper<AiPipeline> query = new QueryWrapper<>();
        query.lambda().eq(AiPipeline::getTenantId, context.tenantId());
        QueryGenerator.installAuthMplus(query, AiPipeline.class, authorization.rules(),
                authorization.user(), context.tenantId());
        return query;
    }

    private Authorization authorization(AgentAccessContext context) {
        if (context == null || !StringUtils.hasText(context.username()) || !StringUtils.hasText(context.tenantId())) {
            throw notFound();
        }
        SysUserCacheInfo user = commonApi.getCacheUser(context.username());
        Set<String> permissions = user == null ? Set.of() : commonApi.queryUserAuths(user.getSysUserId());
        if (user == null || permissions == null || !permissions.contains("ai:pipeline:list")) throw notFound();
        List<SysPermissionDataRuleModel> rules = commonApi.queryPermissionDataRule(COMPONENT, PATH, context.username());
        return new Authorization(user, rules == null ? List.of() : rules);
    }

    private AiPipeline requireOne(AiPipeline pipeline) {
        if (pipeline == null) throw notFound();
        return pipeline;
    }

    private PipelineException notFound() {
        return PipelineException.of(PipelineErrorCode.PIPELINE_NOT_FOUND_OR_FORBIDDEN,
                "Pipeline was not found or is outside the authorized data scope", null);
    }

    private <T> T withTenant(String tenantId, Supplier<T> action) {
        String previous = TenantContext.getTenant();
        try {
            TenantContext.setTenant(tenantId);
            return action.get();
        } finally {
            // Resolver calls may run on shared workers; always restore the prior tenant context.
            if (StringUtils.hasText(previous)) TenantContext.setTenant(previous); else TenantContext.clear();
        }
    }

    private record Authorization(SysUserCacheInfo user, List<SysPermissionDataRuleModel> rules) {
    }
}
