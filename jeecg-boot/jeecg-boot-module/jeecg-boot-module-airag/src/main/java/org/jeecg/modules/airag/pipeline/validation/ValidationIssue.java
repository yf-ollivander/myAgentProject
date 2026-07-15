package org.jeecg.modules.airag.pipeline.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue implements Serializable, Comparable<ValidationIssue> {
    private String scope;
    private String nodeId;
    private String edgeId;
    private String field;
    private String code;
    private String message;

    @Override
    public int compareTo(ValidationIssue other) {
        return key(scope).compareTo(key(other.scope)) != 0
                ? key(scope).compareTo(key(other.scope))
                : joinedKey().compareTo(other.joinedKey());
    }

    private String joinedKey() {
        return String.join("\u0000", key(nodeId), key(edgeId), key(field), key(code));
    }

    private static String key(String value) {
        return value == null ? "" : value;
    }
}
