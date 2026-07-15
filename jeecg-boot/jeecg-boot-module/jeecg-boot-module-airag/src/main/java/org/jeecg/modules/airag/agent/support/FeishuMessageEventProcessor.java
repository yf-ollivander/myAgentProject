package org.jeecg.modules.airag.agent.support;

@FunctionalInterface
public interface FeishuMessageEventProcessor {
    void process(FeishuInboundMessage message);
}
