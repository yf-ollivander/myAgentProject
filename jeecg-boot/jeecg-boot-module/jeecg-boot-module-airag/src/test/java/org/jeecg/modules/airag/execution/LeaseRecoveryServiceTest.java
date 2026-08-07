package org.jeecg.modules.airag.execution;

import org.jeecg.common.config.TenantContext;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.mapper.*;
import org.jeecg.modules.airag.execution.scheduler.LeaseRecoveryService;
import org.jeecg.modules.airag.execution.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LeaseRecoveryServiceTest {
    @AfterEach void clear(){TenantContext.clear();}
    @Test void disabledRecoveryDoesNotTouchDatabaseAndTenantScopeRestoresCaller(){AiExecutorProperties properties=new AiExecutorProperties();properties.setEnabled(false);AiNodeRunMapper nodes=mock(AiNodeRunMapper.class);ExecutionTenantScope scope=new ExecutionTenantScope();LeaseRecoveryService service=new LeaseRecoveryService(nodes,mock(AiRunMapper.class),properties,mock(ExecutionOutboxService.class),mock(RunEventService.class),mock(TransactionTemplate.class),scope);
        TenantContext.setTenant("original");scope.run("worker",()->assertEquals("worker",TenantContext.getTenant()));assertEquals("original",TenantContext.getTenant());service.recover();verifyNoInteractions(nodes);}
}
