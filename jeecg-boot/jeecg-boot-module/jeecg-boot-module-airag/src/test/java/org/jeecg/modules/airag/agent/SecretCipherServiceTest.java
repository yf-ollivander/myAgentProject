package org.jeecg.modules.airag.agent;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherServiceTest {
    @Test
    void encryptsWithRandomNonceAndDecrypts() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setSecretKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        SecretCipherService cipher = new SecretCipherService(properties);

        String first = cipher.encrypt("sensitive-value");
        String second = cipher.encrypt("sensitive-value");

        assertNotEquals(first, second);
        assertEquals("sensitive-value", cipher.decrypt(first));
        assertTrue(first.startsWith("v1:"));
    }

    @Test
    void rejectsMissingOrWrongKey() {
        SecretCipherService missing = new SecretCipherService(new AiAgentProperties());
        assertThrows(JeecgBootException.class, () -> missing.encrypt("secret"));

        AiAgentProperties invalidProperties = new AiAgentProperties();
        invalidProperties.setSecretKey("short");
        SecretCipherService invalid = new SecretCipherService(invalidProperties);
        assertThrows(JeecgBootException.class, () -> invalid.encrypt("secret"));
    }
}
