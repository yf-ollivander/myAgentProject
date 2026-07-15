package org.jeecg.modules.airag.pipeline.contract;

import lombok.Data;

import java.io.Serializable;

@Data
public class PipelineEdge implements Serializable {
    private String id;
    private String source;
    private String target;
    private PipelineEnums.EdgeBranch branch;
}
