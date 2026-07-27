package org.jeecg.modules.airag.execution.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.entity.AiRun;
import org.jeecg.modules.airag.execution.mapper.AiRunMapper;
import org.jeecg.modules.airag.execution.service.RunDependencyAvailabilityService;
import org.jeecg.modules.airag.execution.service.ExecutionTenantScope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

@Component
public class DependencyCancellationScheduler {
    private final AiRunMapper runMapper;private final RunDependencyAvailabilityService dependencies;private final TransactionTemplate transactions;private final AiExecutorProperties properties;private final ExecutionTenantScope tenantScope;
    public DependencyCancellationScheduler(AiRunMapper runMapper,RunDependencyAvailabilityService dependencies,TransactionTemplate transactions,AiExecutorProperties properties,ExecutionTenantScope tenantScope){this.runMapper=runMapper;this.dependencies=dependencies;this.transactions=transactions;this.properties=properties;this.tenantScope=tenantScope;}
    @Scheduled(fixedDelayString="#{${ai.executor.dependency-scan-seconds:30} * 1000}")
    public void scan(){if(!properties.isEnabled())return;runMapper.selectList(new QueryWrapper<AiRun>().lambda().in(AiRun::getStatus,List.of("CREATED","RUNNING","WAITING")).eq(AiRun::getDelFlag,0).last("LIMIT 200")).forEach(candidate->tenantScope.run(candidate.getTenantId(),()->transactions.executeWithoutResult(s->{AiRun run=runMapper.selectByIdForUpdate(candidate.getId(),candidate.getTenantId());if(run!=null&&!dependencies.available(run))dependencies.cancelUnavailableLocked(run,"DEPENDENCY_DISABLED");})));}
}
