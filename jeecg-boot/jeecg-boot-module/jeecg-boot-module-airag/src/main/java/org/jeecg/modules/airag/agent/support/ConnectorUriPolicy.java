package org.jeecg.modules.airag.agent.support;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

@Component
public class ConnectorUriPolicy {
    private final AiAgentProperties properties;

    public ConnectorUriPolicy(AiAgentProperties properties) {
        this.properties = properties;
    }

    public URI resolve(String baseUrl, String path) {
        try {
            URI base = URI.create(baseUrl.trim());
            validateBase(base);
            String endpoint = baseUrl.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
            URI resolved = URI.create(endpoint);
            validateBase(resolved);
            return resolved;
        } catch (IllegalArgumentException e) {
            throw new JeecgBootException("Connector URL is invalid");
        }
    }

    private void validateBase(URI uri) {
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new JeecgBootException("Connector URL only supports HTTP or HTTPS");
        }
        if (!StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getQuery() != null) {
            throw new JeecgBootException("Connector URL host is invalid and user info, query strings, or fragments are not allowed");
        }
        if (!isAllowed(uri.getHost())) {
            throw new JeecgBootException("Connector host is not in ai.agent.allowed-hosts");
        }
    }

    private boolean isAllowed(String host) {
        if (properties.getAllowedHosts() == null || properties.getAllowedHosts().isEmpty()) {
            return true;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return properties.getAllowedHosts().stream().filter(StringUtils::hasText).anyMatch(pattern -> {
            String value = pattern.trim().toLowerCase(Locale.ROOT);
            return value.startsWith("*.")
                    ? normalized.endsWith(value.substring(1)) && normalized.length() > value.length() - 1
                    : normalized.equals(value);
        });
    }
}
