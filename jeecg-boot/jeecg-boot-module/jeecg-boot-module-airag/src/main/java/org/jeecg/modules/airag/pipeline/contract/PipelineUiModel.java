package org.jeecg.modules.airag.pipeline.contract;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PipelineUiModel implements Serializable {
    private List<UiNode> nodes = new ArrayList<>();
    private Viewport viewport;

    @Data
    public static class UiNode implements Serializable {
        private String id;
        private Double x;
        private Double y;
        private Double width;
        private Double height;
    }

    @Data
    public static class Viewport implements Serializable {
        private Double x;
        private Double y;
        private Double zoom;
    }
}
