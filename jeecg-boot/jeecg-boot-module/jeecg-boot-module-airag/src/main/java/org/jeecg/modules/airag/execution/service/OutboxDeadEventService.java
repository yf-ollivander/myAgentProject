package org.jeecg.modules.airag.execution.service;

import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.AiRunMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class OutboxDeadEventService {
    private final AiRunMapper runMapper;private final RunEventService events;
    public OutboxDeadEventService(AiRunMapper runMapper,RunEventService events){this.runMapper=runMapper;this.events=events;}
    @Transactional(rollbackFor=Exception.class)
    public void record(AiOutbox outbox){AiRun run=runMapper.selectByIdForUpdate(outbox.getAggregateId(),outbox.getTenantId());if(run!=null)events.append(run,null,"OUTBOX_DEAD",null,null,"Outbox delivery exhausted",null,UUID.randomUUID().toString(),null);}
}
