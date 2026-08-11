package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.entity.AiConnector;
import org.jeecg.modules.airag.agent.mapper.AiConnectorMapper;
import org.jeecg.modules.airag.agent.model.ConnectorHttpTransport;
import org.jeecg.modules.airag.agent.model.ConnectorInvocationService;
import org.jeecg.modules.airag.agent.model.ModelCallService;
import org.jeecg.modules.airag.agent.support.ConnectorUriPolicy;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.jeecg.modules.airag.execution.gateway.AgentExecutionException;
import org.jeecg.modules.airag.execution.gateway.AgentExecutionGateway.AgentExecutionRequest;
import org.jeecg.modules.airag.execution.gateway.HttpAgentExecutionGateway;
import org.jeecg.modules.airag.pipeline.contract.AgentResultContract;
import org.jeecg.modules.airag.pipeline.validation.AgentResultValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpAgentExecutionGatewayTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsStableResult11RequestAndAcceptsStrictResponse() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/execute", exchange -> {
            requestBody.set(mapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "{\"contractVersion\":\"1.1\",\"status\":\"SUCCESS\",\"summary\":\"ok\",\"output\":{\"done\":true},\"artifacts\":[],\"needsUser\":false,\"retryable\":false}");
        });
        server.start();

        HttpAgentExecutionGateway gateway = gateway(mapper, "127.0.0.1");
        AgentResultContract result = gateway.execute(request(mapper));

        assertEquals("ok", result.getSummary());
        assertEquals("invocation-1", requestBody.get().path("requestId").asText());
        assertEquals("1.1", requestBody.get().path("contractVersion").asText());
        assertEquals("run-1", requestBody.get().path("runId").asText());
    }

    @Test
    void rejectsUnknownResponseFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/execute", exchange -> respond(exchange, 200,
                "{\"contractVersion\":\"1.1\",\"status\":\"SUCCESS\",\"artifacts\":[],\"needsUser\":false,\"retryable\":false,\"unexpected\":true}"));
        server.start();

        AgentExecutionException error = assertThrows(AgentExecutionException.class,
                () -> gateway(mapper, "127.0.0.1").execute(request(mapper)));

        assertEquals("CONNECTOR_RESPONSE_INVALID", error.getErrorCode());
        assertFalse(error.isRetryable());
    }

    @Test
    void failsClosedWhenHttpAllowlistIsEmpty() {
        ObjectMapper mapper = new ObjectMapper();
        AgentExecutionException error = assertThrows(AgentExecutionException.class,
                () -> gateway(mapper, null).execute(request(mapper)));

        assertEquals("CONNECTOR_HOST_NOT_ALLOWED", error.getErrorCode());
    }

    private HttpAgentExecutionGateway gateway(ObjectMapper mapper, String allowedHost) {
        AiAgentProperties agentProperties = new AiAgentProperties();
        agentProperties.setAllowedHosts(allowedHost == null ? List.of() : List.of(allowedHost));
        AiConnector current = new AiConnector();
        current.setId("connector-1");
        current.setTenantId("0");
        current.setEnabled(true);
        current.setResultContractVersion("LEGACY");
        AiConnectorMapper connectorMapper = mock(AiConnectorMapper.class);
        when(connectorMapper.selectOne(any())).thenReturn(current);
        // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】正式 Gateway 测试复用统一 Transport 和调用服务-----------
        SecretCipherService cipher = new SecretCipherService(agentProperties);
        ConnectorUriPolicy uriPolicy = new ConnectorUriPolicy(agentProperties);
        ConnectorInvocationService invocationService = new ConnectorInvocationService(mapper,
                new ConnectorHttpTransport(uriPolicy, cipher, agentProperties, new AiExecutorProperties()),
                new ModelCallService(List.of()), new AgentResultValidator(), cipher);
        return new HttpAgentExecutionGateway(connectorMapper, invocationService);
        // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】正式 Gateway 测试复用统一 Transport 和调用服务-----------
    }

    private AgentExecutionRequest request(ObjectMapper mapper) {
        AgentConfigSnapshot snapshot = AgentConfigSnapshot.builder().agentId("agent-1").agentCode("worker")
                .systemPrompt("system").connector(AgentConfigSnapshot.ConnectorSnapshot.builder()
                        .connectorId("connector-1").baseUrl("http://127.0.0.1:" + (server == null ? 1 : server.getAddress().getPort()))
                        .path("/execute").authType("NONE").resultContractVersion("1.1")
                        .connectTimeout(2).readTimeout(2).build()).build();
        return new AgentExecutionRequest("invocation-1", "run-1", "0", "node-1", 1, 0,
                "trace-1", snapshot, mapper.createObjectNode().put("task", "test"), null, List.of());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
