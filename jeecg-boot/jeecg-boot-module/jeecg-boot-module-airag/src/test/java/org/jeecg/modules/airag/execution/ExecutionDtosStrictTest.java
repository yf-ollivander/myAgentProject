package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.*;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.RunCreateRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionDtosStrictTest {
    @Test void authorityAndVersionFieldsAreRejectedEvenWhenMapperIgnoresUnknowns(){ObjectMapper mapper=new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);assertThrows(JsonMappingException.class,()->mapper.readValue("{\"requestId\":\"r\",\"runType\":\"PIPELINE\",\"pipelineId\":\"p\",\"input\":{},\"tenantId\":\"other\"}",RunCreateRequest.class));assertThrows(JsonMappingException.class,()->mapper.readValue("{\"requestId\":\"r\",\"runType\":\"PIPELINE\",\"pipelineId\":\"p\",\"pipelineVersionId\":\"v\",\"input\":{}}",RunCreateRequest.class));}
}
