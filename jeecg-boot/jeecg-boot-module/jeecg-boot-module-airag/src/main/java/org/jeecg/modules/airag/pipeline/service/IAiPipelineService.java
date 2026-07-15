package org.jeecg.modules.airag.pipeline.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.airag.pipeline.dto.PipelineDtos;

import java.util.List;

public interface IAiPipelineService {
    IPage<PipelineDtos.ListItem> page(String code, String name, Boolean enabled, int pageNo, int pageSize);
    PipelineDtos.CreateResult create(PipelineDtos.CreateRequest request);
    PipelineDtos.Detail get(String id);
    void delete(String id);
    PipelineDtos.DraftView getDraft(String id);
    long saveDraft(String id, PipelineDtos.DraftSaveRequest request);
    PipelineDtos.ValidationResult validate(String id, long revision);
    PipelineDtos.PublishResult publish(String id, PipelineDtos.PublishRequest request);
    IPage<PipelineDtos.VersionSummary> versions(String id, int pageNo, int pageSize);
    PipelineDtos.VersionView version(String id, int version);
    void enable(String id);
    void disable(String id);
    List<PipelineDtos.PipelineOption> options(String keyword, int limit);
}
