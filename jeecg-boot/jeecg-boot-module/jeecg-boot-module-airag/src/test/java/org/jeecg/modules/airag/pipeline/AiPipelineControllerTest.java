package org.jeecg.modules.airag.pipeline;

import org.jeecg.modules.airag.pipeline.controller.AiPipelineController;
import org.jeecg.modules.airag.pipeline.dto.PipelineDtos;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineBotResolver;
import org.jeecg.modules.airag.pipeline.service.IAiPipelineService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiPipelineControllerTest {
    @Test
    void createReturnsServiceContract() {
        IAiPipelineService service = mock(IAiPipelineService.class);
        PipelineDtos.CreateRequest request = new PipelineDtos.CreateRequest(); request.setCode("sample"); request.setName("Sample");
        when(service.create(request)).thenReturn(new PipelineDtos.CreateResult("p1", 0));
        AiPipelineController controller = new AiPipelineController(service, mock(AuthorizedPipelineBotResolver.class));
        assertEquals("p1", controller.create(request).getResult().id());
    }
}
