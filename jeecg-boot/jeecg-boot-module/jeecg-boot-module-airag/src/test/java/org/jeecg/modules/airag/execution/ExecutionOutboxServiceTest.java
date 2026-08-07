package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.jeecg.modules.airag.execution.entity.*;
import org.jeecg.modules.airag.execution.mapper.AiOutboxMapper;
import org.jeecg.modules.airag.execution.service.ExecutionOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionOutboxServiceTest {
    @Test void taskPayloadContainsOnlyStableIdentifiers()throws Exception{AiOutboxMapper mapper=mock(AiOutboxMapper.class);ExecutionOutboxService service=new ExecutionOutboxService(mapper,new ObjectMapper());AiRun run=new AiRun();run.setId("r");run.setTenantId("0");AiNodeRun node=new AiNodeRun();node.setId("nr");node.setDispatchVersion(2L);service.nodeReady(run,node,"trace",null);ArgumentCaptor<AiOutbox> captor=ArgumentCaptor.forClass(AiOutbox.class);verify(mapper).insert(captor.capture());JsonNode payload=new ObjectMapper().readTree(captor.getValue().getPayloadJson());Set<String> fields=new java.util.HashSet<>();payload.fieldNames().forEachRemaining(fields::add);assertEquals(Set.of("eventId","runId","nodeRunId","dispatchVersion","traceId"),fields);}
    @Test void notificationIsInsertedDirectlyWithoutTaskUpdate(){AiOutboxMapper mapper=mock(AiOutboxMapper.class);ExecutionOutboxService service=new ExecutionOutboxService(mapper,new ObjectMapper());AiRun run=new AiRun();run.setId("r");run.setTenantId("0");AiNodeRun node=new AiNodeRun();node.setId("nr");node.setDispatchVersion(1L);service.notification(run,node,"trace","message");ArgumentCaptor<AiOutbox> captor=ArgumentCaptor.forClass(AiOutbox.class);verify(mapper,times(1)).insert(captor.capture());verify(mapper,never()).updateById(any(AiOutbox.class));assertEquals("NOTIFICATION",captor.getValue().getDestination());assertEquals("NODE_NOTIFY",captor.getValue().getEventType());}
}
