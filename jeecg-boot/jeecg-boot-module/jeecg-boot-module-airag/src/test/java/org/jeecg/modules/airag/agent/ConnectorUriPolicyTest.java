package org.jeecg.modules.airag.agent;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.support.ConnectorUriPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectorUriPolicyTest {
    @Test
    void resolvesAllowedHttpEndpoint() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setAllowedHosts(List.of("agent.internal", "*.example.com"));
        ConnectorUriPolicy policy = new ConnectorUriPolicy(properties);

        assertEquals("https://agent.internal/api/execute", policy.resolve("https://agent.internal", "/api/execute").toString());
        assertEquals("https://east.example.com/run", policy.resolve("https://east.example.com", "run").toString());
    }

    @Test
    void rejectsUnsafeOrUnlistedEndpoints() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setAllowedHosts(List.of("agent.internal"));
        ConnectorUriPolicy policy = new ConnectorUriPolicy(properties);

        assertThrows(JeecgBootException.class, () -> policy.resolve("file:///tmp/secret", "/read"));
        assertThrows(JeecgBootException.class, () -> policy.resolve("https://user:pass@agent.internal", "/run"));
        assertThrows(JeecgBootException.class, () -> policy.resolve("https://other.internal", "/run"));
    }
}
