package org.jeecg.modules.airag.execution;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.jeecg.modules.airag.execution.service.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PipelineTemplateResolverTest {
    private final ObjectMapper mapper=new ObjectMapper();private final PipelineTemplateResolver resolver=new PipelineTemplateResolver();
    @Test void exactPlaceholderPreservesJsonType()throws Exception{JsonNode value=resolver.resolve(TextNode.valueOf("{{run.input.count}}"),mapper.readTree("{\"count\":3}"),Map.of(),Map.of());assertTrue(value.isInt());assertEquals(3,value.intValue());}
    @Test void embeddedPlaceholderBecomesText()throws Exception{JsonNode value=resolver.resolve(TextNode.valueOf("count={{run.input.count}}"),mapper.readTree("{\"count\":3}"),Map.of(),Map.of());assertEquals("count=3",value.textValue());}
    @Test void rejectsScriptLikeExpressions(){assertThrows(ExecutionException.class,()->resolver.resolve(TextNode.valueOf("{{T(java.lang.Runtime)}}"),NullNode.instance,Map.of(),Map.of()));}
    @Test void resolvesNodeOutputAndSummary()throws Exception{assertEquals("ok: approved",resolver.resolve(TextNode.valueOf("{{nodes.review.output.status}}: {{nodes.review.summary}}"),NullNode.instance,Map.of("review",mapper.readTree("{\"status\":\"ok\"}")),Map.of("review","approved")).textValue());}
}
