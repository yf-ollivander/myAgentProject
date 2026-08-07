package org.jeecg.modules.airag.agent;

import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.support.AgentConfigSnapshotSanitizer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigSnapshotSanitizerTest {
    @Test
    void removesCredentialHeadersWithoutMutatingSource() {
        Map<String,String> headers=new LinkedHashMap<>();headers.put("X-Trace","visible");headers.put("X-Credential-Id","hidden");headers.put("X-Custom-Auth","hidden");headers.put("Api-Token","hidden");
        AgentConfigSnapshot source=AgentConfigSnapshot.builder().agentId("a").connector(
                AgentConfigSnapshot.ConnectorSnapshot.builder().connectorId("c").authHeader("X-Custom-Auth")
                        .requestHeaders(headers).secretConfigured(true).build()).build();

        AgentConfigSnapshot sanitized=new AgentConfigSnapshotSanitizer().sanitize(source);

        assertEquals(Map.of("X-Trace","visible"),sanitized.getConnector().getRequestHeaders());
        assertEquals(4,source.getConnector().getRequestHeaders().size());
        assertEquals("X-Custom-Auth",sanitized.getConnector().getAuthHeader());
    }

    @Test
    void producesStableHeaderOrder() {
        AgentConfigSnapshot source=AgentConfigSnapshot.builder().agentId("a").connector(
                AgentConfigSnapshot.ConnectorSnapshot.builder().connectorId("c")
                        .requestHeaders(Map.of("z-header","z","A-Header","a")).build()).build();

        assertEquals(java.util.List.of("A-Header","z-header"),
                new java.util.ArrayList<>(new AgentConfigSnapshotSanitizer().sanitize(source).getConnector().getRequestHeaders().keySet()));
    }

    @Test
    void removesLegacyMappingFromResult11Snapshot() {
        AiConfigDtos.ResponseMapping mapping = new AiConfigDtos.ResponseMapping();
        mapping.setSuccessPointer("/success");
        AgentConfigSnapshot source = AgentConfigSnapshot.builder().agentId("a").connector(
                AgentConfigSnapshot.ConnectorSnapshot.builder().connectorId("c")
                        .resultContractVersion("1.1").responseMapping(mapping).build()).build();

        AgentConfigSnapshot sanitized = new AgentConfigSnapshotSanitizer().sanitize(source);

        assertNull(sanitized.getConnector().getResponseMapping());
    }
}
