package org.jeecg.modules.airag.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "ai.agent")
public class AiAgentProperties {
    private String secretKey = System.getenv("AI_CONFIG_SECRET_KEY");
    private String callbackBaseUrl = System.getenv("AI_CALLBACK_BASE_URL");
    private String feishuApiBaseUrl = envOrDefault("AI_FEISHU_API_BASE_URL", "https://open.feishu.cn");
    private boolean mockEnabled = Boolean.parseBoolean(System.getenv("AI_MOCK_AGENT_ENABLED"));
    private List<String> allowedHosts = readAllowedHosts();

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<String> readAllowedHosts() {
        String value = System.getenv("AI_AGENT_ALLOWED_HOSTS");
        return value == null || value.isBlank() ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(value.split(",")));
    }
}
