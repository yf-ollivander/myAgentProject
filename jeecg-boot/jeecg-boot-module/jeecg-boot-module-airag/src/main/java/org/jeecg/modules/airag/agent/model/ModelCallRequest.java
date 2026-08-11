package org.jeecg.modules.airag.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.pipeline.contract.ArtifactDescriptor;

import java.util.List;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】定义厂商无关的内部模型调用请求-----------
@Value
@Builder
public class ModelCallRequest {
    String requestId;
    String modelName;
    String systemPrompt;
    String userPrompt;
    JsonNode resumeInput;
    List<ArtifactDescriptor> artifactInputs;
    AiConfigDtos.ModelOptions modelOptions;
    ModelResponseMode responseMode;
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】定义厂商无关的内部模型调用请求-----------
