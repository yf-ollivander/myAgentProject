package org.jeecg.modules.airag.pipeline.contract;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class StartNodeConfig implements Serializable {
    private List<FieldSchema> inputSchema = new ArrayList<>();
}
