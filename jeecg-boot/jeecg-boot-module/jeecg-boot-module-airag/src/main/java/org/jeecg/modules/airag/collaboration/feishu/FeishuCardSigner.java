package org.jeecg.modules.airag.collaboration.feishu;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class FeishuCardSigner {
    private final AiAgentProperties properties; private final ObjectMapper canonical;
    public FeishuCardSigner(AiAgentProperties properties,ObjectMapper mapper){this.properties=properties;this.canonical=mapper.copy().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);}
    public String sign(ObjectNode value){try{ObjectNode unsigned=value.deepCopy();unsigned.remove("signature");Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(derive(),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(canonical.writeValueAsBytes(unsigned)));}catch(Exception e){throw new IllegalStateException("Card signature failed",e);}}
    public boolean verify(ObjectNode value){String actual=value.path("signature").asText();return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),sign(value).getBytes(StandardCharsets.UTF_8));}
    private byte[] derive()throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(properties.getSecretKey().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return mac.doFinal("feishu-card-action-v1".getBytes(StandardCharsets.UTF_8));}
}
