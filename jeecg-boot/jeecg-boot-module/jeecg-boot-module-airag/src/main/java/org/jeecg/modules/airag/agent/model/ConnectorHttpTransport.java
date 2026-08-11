package org.jeecg.modules.airag.agent.model;

import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.support.ConnectorUriPolicy;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.execution.config.AiExecutorProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】集中执行 Connector HTTP 安全、大小和错误策略-----------
@Component
public class ConnectorHttpTransport {
    private static final Set<String> PROTECTED_SYSTEM_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "host",
            "content-type", "content-length", "transfer-encoding");
    private final ConnectorUriPolicy uriPolicy;
    private final SecretCipherService cipher;
    private final AiAgentProperties agentProperties;
    private final AiExecutorProperties executorProperties;

    public ConnectorHttpTransport(ConnectorUriPolicy uriPolicy, SecretCipherService cipher,
                                  AiAgentProperties agentProperties, AiExecutorProperties executorProperties) {
        this.uriPolicy = uriPolicy;
        this.cipher = cipher;
        this.agentProperties = agentProperties;
        this.executorProperties = executorProperties;
    }

    public ConnectorHttpResponse postJson(ConnectorRuntimeConfig config, byte[] requestBody) {
        if (requestBody.length > executorProperties.getConnectorMaxRequestBytes()) {
            throw ConnectorCallException.nonRetryable("CONNECTOR_REQUEST_TOO_LARGE", "Connector request is too large");
        }
        URI endpoint = endpoint(config);
        rejectUnsafeResolution(endpoint);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(Math.max(1, config.getReadTimeout())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
        // update-begin---author:Codex ---date:2026-08-10  for:【REQ-HTTP-MODEL-20260810】运行时再次阻止自定义 Header 覆盖系统传输或鉴权 Header-----------
        config.getRequestHeaders().forEach((name, value) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            boolean authHeaderCollision = StringUtils.hasText(config.getAuthHeader())
                    && config.getAuthHeader().equalsIgnoreCase(name);
            if (!PROTECTED_SYSTEM_HEADERS.contains(normalized) && !authHeaderCollision) {
                builder.header(name, value);
            }
        });
        // update-end---author:Codex ---date:2026-08-10  for:【REQ-HTTP-MODEL-20260810】运行时再次阻止自定义 Header 覆盖系统传输或鉴权 Header-----------
        applyCredential(builder, config);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(1, config.getConnectTimeout())))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream stream = response.body()) {
                body = readBounded(stream, executorProperties.getConnectorMaxResponseBytes());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() == 408 || response.statusCode() == 429
                        || response.statusCode() >= 500;
                throw new ConnectorCallException("HTTP_" + response.statusCode(),
                        "Connector endpoint returned HTTP " + response.statusCode(), retryable, response.statusCode());
            }
            // update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】在 Adapter 解析前移除响应回显凭据-----------
            return new ConnectorHttpResponse(response.statusCode(), redact(body, config), response.headers());
            // update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】在 Adapter 解析前移除响应回显凭据-----------
        } catch (ConnectorCallException failure) {
            throw failure;
        } catch (HttpTimeoutException timeout) {
            throw new ConnectorCallException("TIMEOUT", "Connector endpoint timed out", true, null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ConnectorCallException("INTERRUPTED", "Connector request was interrupted", false, null);
        } catch (Exception connection) {
            throw new ConnectorCallException("CONNECTION_ERROR", "Connector endpoint connection failed", true, null);
        }
    }

    private URI endpoint(ConnectorRuntimeConfig config) {
        List<String> hosts = agentProperties.getAllowedHosts();
        if (hosts == null || hosts.stream().noneMatch(StringUtils::hasText)) {
            throw ConnectorCallException.nonRetryable("CONNECTOR_HOST_NOT_ALLOWED", "Connector host allowlist is empty");
        }
        try {
            String path = config.getProviderType().resolvePath(config.getPath(), config.getModelName());
            return uriPolicy.resolve(config.getBaseUrl(), path);
        } catch (RuntimeException invalid) {
            throw ConnectorCallException.nonRetryable("CONNECTOR_URL_INVALID", "Connector URL is invalid");
        }
    }

    private void applyCredential(HttpRequest.Builder builder, ConnectorRuntimeConfig config) {
        if (!StringUtils.hasText(config.getSecretCipher()) || "NONE".equals(config.getAuthType())) {
            return;
        }
        String secret = cipher.decrypt(config.getSecretCipher());
        if ("BEARER".equals(config.getAuthType())) {
            builder.header("Authorization", "Bearer " + secret);
        } else if ("API_KEY".equals(config.getAuthType())) {
            builder.header(config.getAuthHeader(), secret);
        }
    }

    private byte[] readBounded(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            total += read;
            if (total > limit) {
                throw ConnectorCallException.nonRetryable("CONNECTOR_RESPONSE_TOO_LARGE", "Connector response is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void rejectUnsafeResolution(URI endpoint) {
        try {
            boolean exactAllow = agentProperties.getAllowedHosts().stream().filter(StringUtils::hasText)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(endpoint.getHost().toLowerCase(Locale.ROOT)::equals);
            for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
                boolean unsafe = address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress();
                if (unsafe && !exactAllow) {
                    throw ConnectorCallException.nonRetryable("CONNECTOR_HOST_UNSAFE", "Connector host resolves to a blocked address");
                }
            }
        } catch (ConnectorCallException failure) {
            throw failure;
        } catch (Exception unresolved) {
            throw new ConnectorCallException("CONNECTION_ERROR", "Connector host cannot be resolved", true, null);
        }
    }

    private byte[] redact(byte[] body, ConnectorRuntimeConfig config) {
        String value = new String(body, StandardCharsets.UTF_8);
        if (StringUtils.hasText(config.getSecretCipher())) {
            String secret = cipher.decrypt(config.getSecretCipher());
            if (StringUtils.hasText(secret)) value = value.replace(secret, "***");
        }
        for (String header : config.getRequestHeaders().values()) {
            if (StringUtils.hasText(header)) value = value.replace(header, "***");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】集中执行 Connector HTTP 安全、大小和错误策略-----------
