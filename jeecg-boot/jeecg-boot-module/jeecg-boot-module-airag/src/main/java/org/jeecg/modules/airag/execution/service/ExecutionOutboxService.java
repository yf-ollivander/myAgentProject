package org.jeecg.modules.airag.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.AiOutboxMapper;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.UUID;

@Service
public class ExecutionOutboxService {
    private final AiOutboxMapper mapper; private final ObjectMapper objectMapper;
    public ExecutionOutboxService(AiOutboxMapper mapper, ObjectMapper objectMapper) { this.mapper=mapper; this.objectMapper=objectMapper; }

    public AiOutbox nodeReady(AiRun run, AiNodeRun node, String traceId, Date dueAt) {
        AiOutbox outbox = base(run,node,traceId,dueAt,OutboxDestination.TASK,"NODE_READY");
        ObjectNode payload=objectMapper.createObjectNode(); payload.put("eventId",outbox.getEventId()); payload.put("runId",run.getId());
        payload.put("nodeRunId",node.getId()); payload.put("dispatchVersion",node.getDispatchVersion()); payload.put("traceId",traceId);
        outbox.setPayloadJson(payload.toString()); mapper.insert(outbox); return outbox;
    }

    public AiOutbox notification(AiRun run, AiNodeRun node, String traceId, String message) {
        AiOutbox outbox = base(run,node,traceId,new Date(),OutboxDestination.NOTIFICATION,"NOTIFICATION");
        ObjectNode payload=objectMapper.createObjectNode(); payload.put("eventId",outbox.getEventId()); payload.put("runId",run.getId());
        payload.put("nodeRunId",node.getId()); payload.put("dispatchVersion",node.getDispatchVersion()); payload.put("traceId",traceId);
        payload.put("message", message); outbox.setPayloadJson(payload.toString()); mapper.insert(outbox); return outbox;
    }

    private AiOutbox base(AiRun run,AiNodeRun node,String traceId,Date dueAt,OutboxDestination destination,String eventType){AiOutbox outbox=new AiOutbox();outbox.setTenantId(run.getTenantId());outbox.setEventId(UUID.randomUUID().toString());outbox.setDestination(destination.name());outbox.setEventType(eventType);outbox.setAggregateId(run.getId());outbox.setStatus(OutboxStatus.PENDING.name());outbox.setRetryCount(0);outbox.setNextRetryAt(dueAt==null?new Date():dueAt);outbox.setCreateTime(new Date());return outbox;
    }
}
