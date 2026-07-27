package org.jeecg.modules.airag.execution.service;

import org.jeecg.common.config.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

@Component
public class ExecutionTenantScope {
    public void run(String tenantId, Runnable action) {
        call(tenantId, () -> {
            action.run();
            return null;
        });
    }

    public <T> T call(String tenantId, Supplier<T> action) {
        String previous = TenantContext.getTenant();
        try {
            TenantContext.setTenant(StringUtils.hasText(tenantId) ? tenantId : "0");
            return action.get();
        } finally {
            // Scheduler and executor threads are reused, so tenant state must never escape one task.
            if (StringUtils.hasText(previous)) TenantContext.setTenant(previous); else TenantContext.clear();
        }
    }
}
