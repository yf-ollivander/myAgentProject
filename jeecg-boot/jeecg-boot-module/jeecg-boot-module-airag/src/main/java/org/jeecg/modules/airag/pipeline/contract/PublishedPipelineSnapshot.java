package org.jeecg.modules.airag.pipeline.contract;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Value
@Builder
public class PublishedPipelineSnapshot implements Serializable {
    String pipelineId;
    String versionId;
    Integer version;
    String tenantId;
    String definitionHash;
    PipelineDefinition definition;
}
