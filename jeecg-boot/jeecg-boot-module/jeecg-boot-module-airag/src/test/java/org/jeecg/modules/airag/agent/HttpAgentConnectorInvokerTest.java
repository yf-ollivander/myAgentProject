package org.jeecg.modules.airag.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.support.ConnectorUriPolicy;
import org.jeecg.modules.airag.agent.support.HttpAgentConnectorInvoker;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpAgentConnectorInvokerTest {
    @Test
    void coversAuthenticationFailuresTimeoutMappingAndRedaction() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/success", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            apiKey.set(exchange.getRequestHeaders().getFirst("X-Test-Key"));
            respond(exchange, 200, "{\"success\":true,\"output\":{\"echo\":\"top-secret\"},\"summary\":\"ok\"}");
        });
        server.createContext("/unauthorized", exchange -> respond(exchange, 401, "denied"));
        server.createContext("/error", exchange -> respond(exchange, 500, "failed"));
        server.createContext("/invalid", exchange -> respond(exchange, 200, "not-json"));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
                respond(exchange, 200, "{\"success\":true,\"output\":{},\"summary\":\"slow\"}");
            } catch (Exception ignored) {
                exchange.close();
            }
        });
        server.start();

        try {
            ObjectMapper mapper = new ObjectMapper();
            AiAgentProperties properties = properties();
            SecretCipherService cipher = new SecretCipherService(properties);
            HttpAgentConnectorInvoker invoker = new HttpAgentConnectorInvoker(
                    mapper, cipher, new ConnectorUriPolicy(properties));
            AiConnector connector = connector(server.getAddress().getPort(), mapper);
            AiAgent agent = new AiAgent();
            agent.setAgentCode("testAgent");
            agent.setSystemPrompt("system prompt");

            connector.setAuthType("BEARER");
            connector.setSecretCipher(cipher.encrypt("top-secret"));
            AiConfigDtos.AgentExecutionResult execution = invoker.execute(agent, connector, mapper.createObjectNode());
            assertTrue(execution.isSuccess());
            assertEquals("Bearer top-secret", authorization.get());
            assertEquals("***", execution.getOutput().path("echo").asText());

            connector.setAuthType("API_KEY");
            connector.setAuthHeader("X-Test-Key");
            invoker.execute(agent, connector, mapper.createObjectNode());
            assertEquals("top-secret", apiKey.get());

            connector.setAuthType("NONE");
            connector.setSecretCipher(null);
            assertTrue(invoker.execute(agent, connector, mapper.createObjectNode()).isSuccess());

            connector.setPath("/unauthorized");
            assertFailure(invoker.test(connector, null), 401, "HTTP 401");
            connector.setPath("/error");
            assertFailure(invoker.test(connector, null), 500, "HTTP 500");
            connector.setPath("/invalid");
            assertFailure(invoker.test(connector, null), 200, "invalid JSON");
            connector.setPath("/slow");
            connector.setReadTimeout(1);
            assertFailure(invoker.test(connector, null), null, "timed out");
        } finally {
            server.stop(0);
        }
    }

    private static AiAgentProperties properties() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setSecretKey(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        properties.setAllowedHosts(List.of("127.0.0.1"));
        return properties;
    }

    private static AiConnector connector(int port, ObjectMapper mapper) throws Exception {
        AiConfigDtos.ResponseMapping mapping = new AiConfigDtos.ResponseMapping();
        AiConnector connector = new AiConnector();
        connector.setBaseUrl("http://127.0.0.1:" + port);
        connector.setPath("/success");
        connector.setRequestHeaders("{}");
        connector.setResponseMapping(mapper.writeValueAsString(mapping));
        connector.setConnectTimeout(2);
        connector.setReadTimeout(2);
        return connector;
    }

    private static void assertFailure(AiConfigDtos.ConnectionTestResult result, Integer status, String message) {
        assertFalse(result.isSuccess());
        assertEquals(status, result.getHttpStatus());
        assertTrue(result.getMessage().contains(message));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
