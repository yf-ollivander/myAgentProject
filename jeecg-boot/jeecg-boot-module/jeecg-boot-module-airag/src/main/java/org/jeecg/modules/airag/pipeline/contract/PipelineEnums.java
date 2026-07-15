package org.jeecg.modules.airag.pipeline.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public final class PipelineEnums {
    private PipelineEnums() {
    }

    public enum NodeType { START, AGENT, CONDITION, NOTIFY, END }

    public enum EdgeBranch { DEFAULT, TRUE, FALSE }

    public enum ConditionOperator { EQ, NE, GT, GE, LT, LE, EMPTY, NOT_EMPTY }

    public enum ValueType {
        STRING("string"), NUMBER("number"), BOOLEAN("boolean"), OBJECT("object"), ARRAY("array");

        private final String value;

        ValueType(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static ValueType fromValue(String value) {
            for (ValueType type : values()) if (type.value.equals(value)) return type;
            throw new IllegalArgumentException("Unsupported value type: " + value);
        }
    }

    public enum ErrorPolicy { INHERIT, WAIT, FAIL }

    public enum InterventionAction { SUPPLY_INPUT, RETRY, CANCEL }

    public enum AgentResultStatus { SUCCESS, NEEDS_INPUT, FAILED }

    public enum ArtifactType {
        TEXT, JSON, FEISHU_DOC, GIT_REPO, COMMIT, BUILD, TEST_REPORT, DEPLOYMENT_URL, OTHER
    }

    public enum SelectionMode { ALL, LATEST }

    public enum TriggerKeyType { CODE, ALIAS }
}
