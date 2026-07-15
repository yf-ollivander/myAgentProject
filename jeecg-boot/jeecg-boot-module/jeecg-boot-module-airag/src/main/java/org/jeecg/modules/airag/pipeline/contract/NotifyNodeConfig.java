package org.jeecg.modules.airag.pipeline.contract;

import lombok.Data;

import java.io.Serializable;

@Data
public class NotifyNodeConfig implements Serializable {
    private String messageTemplate;
}
