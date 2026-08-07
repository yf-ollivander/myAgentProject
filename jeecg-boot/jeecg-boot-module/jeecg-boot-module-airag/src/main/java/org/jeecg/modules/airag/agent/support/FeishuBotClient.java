package org.jeecg.modules.airag.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import org.jeecg.modules.airag.agent.config.AiAgentProperties;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeishuBotClient {
    private final ObjectMapper objectMapper;
    private final SecretCipherService secretCipherService;
    private final AiAgentProperties properties;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public FeishuBotClient(ObjectMapper objectMapper, SecretCipherService secretCipherService,
                           AiAgentProperties properties) {
        this.objectMapper = objectMapper; this.secretCipherService = secretCipherService; this.properties = properties;
    }

    public AiConfigDtos.ConnectionTestResult test(AiFeishuBot bot, String testMessage) {
        long started = System.currentTimeMillis(); AiConfigDtos.ConnectionTestResult result = new AiConfigDtos.ConnectionTestResult(); result.setTestedAt(new Date());
        try { ObjectNode content=objectMapper.createObjectNode().put("text",StringUtils.hasText(testMessage)?testMessage.trim():"Multi-Agent configuration test succeeded");
            DeliveryResult sent=deliver(bot,DeliveryTargetType.CHAT,bot.getDefaultChatId(),DeliveryMessageType.TEXT,content,java.util.UUID.randomUUID().toString());
            result.setSuccess(true);result.setMessage("Feishu test message sent");result.setOutputPreview(null);
        }catch(FeishuApiException e){result.setSuccess(false);result.setHttpStatus(e.getHttpStatus());result.setMessage("Feishu message API rejected the request");}
        catch(Exception e){result.setSuccess(false);result.setMessage("Feishu API connection or credential validation failed");}
        result.setDurationMs(System.currentTimeMillis()-started);return result;
    }

    public DeliveryResult deliver(AiFeishuBot bot, DeliveryTargetType targetType, String targetId,
                                  DeliveryMessageType messageType, JsonNode content, String uuid) {
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        try{return send(client,bot,targetType,targetId,messageType,content,uuid,false);}
        catch(FeishuApiException unauthorized){if(unauthorized.getHttpStatus()!=401)throw unauthorized;tokens.remove(bot.getAppId());return send(client,bot,targetType,targetId,messageType,content,uuid,true);}
    }

    public DeliveryResult sendText(AiFeishuBot bot,String chatId,JsonNode content,String uuid){return deliver(bot,DeliveryTargetType.CHAT,chatId,DeliveryMessageType.TEXT,content,uuid);}
    public DeliveryResult sendPost(AiFeishuBot bot,String chatId,JsonNode content,String uuid){return deliver(bot,DeliveryTargetType.CHAT,chatId,DeliveryMessageType.POST,content,uuid);}
    public DeliveryResult sendCard(AiFeishuBot bot,String chatId,JsonNode content,String uuid){return deliver(bot,DeliveryTargetType.CHAT,chatId,DeliveryMessageType.CARD,content,uuid);}
    public DeliveryResult replyText(AiFeishuBot bot,String messageId,JsonNode content,String uuid){return deliver(bot,DeliveryTargetType.REPLY,messageId,DeliveryMessageType.TEXT,content,uuid);}
    public DeliveryResult replyPost(AiFeishuBot bot,String messageId,JsonNode content,String uuid){return deliver(bot,DeliveryTargetType.REPLY,messageId,DeliveryMessageType.POST,content,uuid);}
    public DeliveryResult replyCard(AiFeishuBot bot,String messageId,JsonNode content,String uuid){return deliver(bot,DeliveryTargetType.REPLY,messageId,DeliveryMessageType.CARD,content,uuid);}

    public void updateCard(AiFeishuBot bot,String callbackToken,JsonNode cardContent){
        try{HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();ObjectNode body=objectMapper.createObjectNode();body.put("token",callbackToken);body.set("card",cardContent);
            HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl()+"/open-apis/interactive/v1/card/update")).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").header("Authorization","Bearer "+token(client,bot)).POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(response.statusCode()<200||response.statusCode()>=300)throw api(response);
        }catch(FeishuApiException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new FeishuApiException(0,false,null,"Card update interrupted");}catch(Exception e){throw new FeishuApiException(0,true,null,"Card update failed");}}

    private DeliveryResult send(HttpClient client,AiFeishuBot bot,DeliveryTargetType targetType,String targetId,DeliveryMessageType type,JsonNode content,String uuid,boolean refreshed){
        try{ObjectNode payload=objectMapper.createObjectNode();String msgType=switch(type){case TEXT->"text";case POST->"post";case CARD->"interactive";};payload.put("msg_type",msgType);payload.put("content",objectMapper.writeValueAsString(content));payload.put("uuid",uuid);
            URI uri;if(targetType==DeliveryTargetType.CHAT){payload.put("receive_id",targetId);uri=URI.create(baseUrl()+"/open-apis/im/v1/messages?receive_id_type="+URLEncoder.encode("chat_id",StandardCharsets.UTF_8));}else{uri=URI.create(baseUrl()+"/open-apis/im/v1/messages/"+URLEncoder.encode(targetId,StandardCharsets.UTF_8)+"/reply");}
            HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").header("Authorization","Bearer "+token(client,bot)).POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));JsonNode body=objectMapper.readTree(response.body());if(response.statusCode()<200||response.statusCode()>=300||body.path("code").asInt(-1)!=0)throw api(response);return new DeliveryResult(body.path("data").path("message_id").asText(uuid));
        }catch(FeishuApiException e){throw e;}catch(HttpTimeoutException e){throw new FeishuApiException(0,true,null,"Feishu API timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new FeishuApiException(0,false,null,"Feishu API interrupted");}catch(Exception e){throw new FeishuApiException(0,true,null,"Feishu API failed");}}

    private String token(HttpClient client,AiFeishuBot bot)throws Exception{CachedToken cached=tokens.get(bot.getAppId());if(cached!=null&&cached.expiresAt()>System.currentTimeMillis()+300_000L)return cached.value();ObjectNode payload=objectMapper.createObjectNode();payload.put("app_id",bot.getAppId());payload.put("app_secret",secretCipherService.decrypt(bot.getAppSecretCipher()));HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl()+"/open-apis/auth/v3/tenant_access_token/internal")).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));JsonNode body=objectMapper.readTree(response.body());if(response.statusCode()<200||response.statusCode()>=300||body.path("code").asInt(-1)!=0||body.path("tenant_access_token").asText().isBlank())throw api(response);String value=body.path("tenant_access_token").asText();long expires=Math.max(600,body.path("expire").asLong(7200));tokens.put(bot.getAppId(),new CachedToken(value,System.currentTimeMillis()+expires*1000L));return value;}
    private FeishuApiException api(HttpResponse<String> response){Integer retry=null;try{retry=Integer.valueOf(response.headers().firstValue("Retry-After").orElse(""));}catch(Exception ignored){}int status=response.statusCode();boolean retryable=status==429||status>=500;return new FeishuApiException(status,retryable,retry,"Feishu API rejected the request");}
    private String baseUrl(){return properties.getFeishuApiBaseUrl().replaceAll("/+$","");}

    private record CachedToken(String value,long expiresAt){}
    public record DeliveryResult(String messageId){}
    @Getter public static class FeishuApiException extends RuntimeException{private final int httpStatus;private final boolean retryable;private final Integer retryAfterSeconds;public FeishuApiException(int httpStatus,boolean retryable,Integer retryAfterSeconds,String message){super(message);this.httpStatus=httpStatus;this.retryable=retryable;this.retryAfterSeconds=retryAfterSeconds;}}
}
