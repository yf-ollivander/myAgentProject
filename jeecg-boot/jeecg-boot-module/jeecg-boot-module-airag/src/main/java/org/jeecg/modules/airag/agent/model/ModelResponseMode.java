package org.jeecg.modules.airag.agent.model;

import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.springframework.util.StringUtils;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】定义模型文本和严格结果两种转换模式-----------
public enum ModelResponseMode {
    TEXT,
    RESULT_1_1;

    public static ModelResponseMode fromNullable(String value) {
        return StringUtils.hasText(value) ? valueOf(value.trim()) : valueOf(AiConfigDtos.RESPONSE_MODE_TEXT);
    }
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】定义模型文本和严格结果两种转换模式-----------
