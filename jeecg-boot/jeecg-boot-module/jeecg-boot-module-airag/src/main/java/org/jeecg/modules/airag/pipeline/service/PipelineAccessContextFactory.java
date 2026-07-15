package org.jeecg.modules.airag.pipeline.service;

import org.apache.shiro.SecurityUtils;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PipelineAccessContextFactory {
    public AgentAccessContext current() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (!(principal instanceof LoginUser user) || !StringUtils.hasText(user.getUsername())) {
            throw new IllegalStateException("Authenticated JEECG user is required");
        }
        String tenant = TenantContext.getTenant();
        return new AgentAccessContext(user.getUsername(), StringUtils.hasText(tenant) ? tenant : "0");
    }
}
