package org.jeecg.modules.airag.agent.model;

// update-begin---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】归一化 Provider token 使用量-----------
public record ModelUsage(Long inputTokens, Long outputTokens, Long totalTokens) {
}
// update-end---author:Codex ---date:2026-08-10  for：【REQ-HTTP-MODEL-20260810】归一化 Provider token 使用量-----------
