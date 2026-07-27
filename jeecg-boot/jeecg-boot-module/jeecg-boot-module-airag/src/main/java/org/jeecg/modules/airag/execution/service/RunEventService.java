package org.jeecg.modules.airag.execution.service;

import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.*;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.UUID;

@Service
public class RunEventService {
    private final AiRunMapper runMapper; private final AiRunEventMapper eventMapper;
    public RunEventService(AiRunMapper runMapper, AiRunEventMapper eventMapper) { this.runMapper=runMapper; this.eventMapper=eventMapper; }

    public void append(AiRun run, String nodeRunId, String type, String from, String to,
                       String summary, String errorCode, String traceId, String payloadJson) {
        runMapper.incrementEventSequence(run.getId(), run.getTenantId());
        long sequence = (run.getEventSequence() == null ? 0 : run.getEventSequence()) + 1;
        run.setEventSequence(sequence);
        AiRunEvent event = new AiRunEvent(); event.setTenantId(run.getTenantId()); event.setRunId(run.getId());
        event.setNodeRunId(nodeRunId); event.setSequence(sequence); event.setEventType(type); event.setFromStatus(from);
        event.setToStatus(to); event.setSummary(abbreviate(summary)); event.setErrorCode(errorCode);
        event.setTraceId(traceId == null ? UUID.randomUUID().toString() : traceId); event.setPayloadJson(payloadJson);
        event.setCreateTime(new Date()); eventMapper.insert(event);
    }
    private String abbreviate(String value) { return value == null || value.length() <= 1000 ? value : value.substring(0, 1000); }
}
