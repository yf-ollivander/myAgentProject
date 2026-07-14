package org.jeecg.modules.airag.agent.support;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecretCipherService {
    private static final String PREFIX = "v1:";
    private static final int NONCE_LENGTH = 12;
    private final AiAgentProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipherService(AiAgentProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        try {
            readKey();
            return true;
        } catch (JeecgBootException ignored) {
            return false;
        }
    }

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, readKey(), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            throw new JeecgBootException("Agent credential encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (!StringUtils.hasText(ciphertext)) {
            return null;
        }
        if (!ciphertext.startsWith(PREFIX)) {
            throw new JeecgBootException("Unsupported Agent credential format");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (payload.length <= NONCE_LENGTH) {
                throw new JeecgBootException("Invalid Agent credential payload");
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] encrypted = new byte[payload.length - NONCE_LENGTH];
            System.arraycopy(payload, 0, nonce, 0, NONCE_LENGTH);
            System.arraycopy(payload, NONCE_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, readKey(), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            throw new JeecgBootException("Agent credential decryption failed; verify AI_CONFIG_SECRET_KEY", e);
        }
    }

    private SecretKeySpec readKey() {
        String configured = properties.getSecretKey();
        if (!StringUtils.hasText(configured)) {
            throw new JeecgBootException("AI_CONFIG_SECRET_KEY is not configured");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException ignored) {
            key = configured.getBytes(StandardCharsets.UTF_8);
        }
        if (key.length != 32) {
            throw new JeecgBootException("AI_CONFIG_SECRET_KEY must be a Base64 encoded 32-byte key or 32 ASCII characters");
        }
        return new SecretKeySpec(key, "AES");
    }
}
