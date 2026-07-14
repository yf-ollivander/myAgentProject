package org.jeecg.modules.airag.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class FeishuBotClient {
    private final ObjectMapper objectMapper;
    private final SecretCipherService secretCipherService;
    private final AiAgentProperties properties;

    public FeishuBotClient(ObjectMapper objectMapper, SecretCipherService secretCipherService,
                           AiAgentProperties properties) {
        this.objectMapper = objectMapper;
        this.secretCipherService = secretCipherService;
        this.properties = properties;
    }

    public AiConfigDtos.ConnectionTestResult test(AiFeishuBot bot, String testMessage) {
        long started = System.currentTimeMillis();
        AiConfigDtos.ConnectionTestResult result = new AiConfigDtos.ConnectionTestResult();
        result.setTestedAt(new Date());
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            String token = getTenantAccessToken(client, bot);
            HttpResponse<String> response = sendTestMessage(client, bot, token, testMessage);
            result.setHttpStatus(response.statusCode());
            JsonNode body = objectMapper.readTree(response.body());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300
                    && body.path("code").asInt(-1) == 0;
            result.setSuccess(success);
            result.setMessage(success ? "Feishu test message sent" : "Feishu message API rejected the request: " + body.path("msg").asText("unknown error"));
        } catch (HttpTimeoutException e) {
            result.setSuccess(false);
            result.setMessage("Feishu API timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setMessage("Feishu API call was interrupted");
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Feishu API connection or credential validation failed");
        }
        result.setDurationMs(System.currentTimeMillis() - started);
        return result;
    }

    private String getTenantAccessToken(HttpClient client, AiFeishuBot bot) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("app_id", bot.getAppId());
        payload.put("app_secret", secretCipherService.decrypt(bot.getAppSecretCipher()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/open-apis/auth/v3/tenant_access_token/internal"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode body = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || body.path("code").asInt(-1) != 0
                || body.path("tenant_access_token").asText().isBlank()) {
            throw new IllegalStateException("Feishu token rejected");
        }
        return body.path("tenant_access_token").asText();
    }

    private HttpResponse<String> sendTestMessage(HttpClient client, AiFeishuBot bot, String token,
                                                 String testMessage) throws Exception {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("text", StringUtils.hasText(testMessage)
                ? testMessage.trim() : "Multi-Agent configuration test succeeded");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("receive_id", bot.getDefaultChatId());
        payload.put("msg_type", "text");
        payload.put("content", objectMapper.writeValueAsString(content));
        URI uri = URI.create(baseUrl() + "/open-apis/im/v1/messages?receive_id_type="
                + URLEncoder.encode("chat_id", StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String baseUrl() {
        return properties.getFeishuApiBaseUrl().replaceAll("/+$", "");
    }
}
