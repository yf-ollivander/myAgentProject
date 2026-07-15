package org.jeecg.modules.airag.pipeline.contract;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ArtifactInput implements Serializable {
    private String name;
    private String sourceNodeId;
    private List<PipelineEnums.ArtifactType> types = new ArrayList<>();
    private Boolean required;
    private PipelineEnums.SelectionMode selectionMode;
}
