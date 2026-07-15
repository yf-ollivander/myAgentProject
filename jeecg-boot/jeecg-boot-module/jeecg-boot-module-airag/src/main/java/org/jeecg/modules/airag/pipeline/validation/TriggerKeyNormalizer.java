package org.jeecg.modules.airag.pipeline.validation;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class TriggerKeyNormalizer {
    public String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            result.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return result.toString();
    }
}
