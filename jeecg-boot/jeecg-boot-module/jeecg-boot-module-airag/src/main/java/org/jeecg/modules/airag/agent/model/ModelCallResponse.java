package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】定义厂商无关的内部模型调用响应-----------
@Value
@Builder
public class ModelCallResponse {
    String text;
    JsonNode structuredOutput;
    String finishReason;
    ModelUsage usage;
    String providerRequestId;
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】定义厂商无关的内部模型调用响应-----------
