package org.jeecg.modules.airag.pipeline;

import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.airag.pipeline.mapper.AiPipelineMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineDraftConcurrencyTest {
    @Test
    void draftUpdateUsesRevisionPredicateAndAtomicIncrement() throws Exception {
        Update update = AiPipelineMapper.class.getMethod("updateDraft", String.class, String.class, long.class,
                String.class, String.class, String.class, String.class).getAnnotation(Update.class);
        String sql = String.join(" ", update.value());
        assertTrue(sql.contains("draft_revision=#{revision}"));
        assertTrue(sql.contains("draft_revision=draft_revision+1"));
        assertTrue(sql.contains("tenant_id=#{tenantId}"));
    }
}
