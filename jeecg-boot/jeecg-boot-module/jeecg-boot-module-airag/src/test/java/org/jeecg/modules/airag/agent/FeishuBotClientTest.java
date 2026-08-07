package org.jeecg.modules.airag.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.support.FeishuBotClient;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FeishuBotClientTest {
    @Test
    void obtainsTenantTokenAndSendsCustomTestMessage() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> messageBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange ->
                respond(exchange, 200, "{\"code\":0,\"tenant_access_token\":\"tenant-token\"}"));
        server.createContext("/open-apis/im/v1/messages", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            messageBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\"}");
        });
        server.start();

        try {
            ObjectMapper mapper = new ObjectMapper();
            AiAgentProperties properties = new AiAgentProperties();
            properties.setSecretKey(Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
            properties.setFeishuApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            SecretCipherService cipher = new SecretCipherService(properties);
            AiFeishuBot bot = new AiFeishuBot();
            bot.setAppId("app-id");
            bot.setAppSecretCipher(cipher.encrypt("app-secret"));
            bot.setDefaultChatId("chat-id");

            AiConfigDtos.ConnectionTestResult result = new FeishuBotClient(mapper, cipher, properties)
                    .test(bot, "custom test message");

            assertTrue(result.isSuccess());
            assertEquals("Bearer tenant-token", authorization.get());
            JsonNode payload = mapper.readTree(messageBody.get());
            assertEquals("chat-id", payload.path("receive_id").asText());
            assertEquals("custom test message",
                    mapper.readTree(payload.path("content").asText()).path("text").asText());
            assertNull(result.getOutputPreview());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
