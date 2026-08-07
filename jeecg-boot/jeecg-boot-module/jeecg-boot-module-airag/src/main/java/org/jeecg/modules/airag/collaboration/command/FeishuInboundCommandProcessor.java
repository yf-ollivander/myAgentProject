package org.jeecg.modules.airag.collaboration.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.mapper.AiFeishuBotMapper;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.support.SecretCipherService;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.*;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.Command;
import org.jeecg.modules.airag.collaboration.entity.*;
import org.jeecg.modules.airag.collaboration.inbox.FeishuInboundEventProcessor;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuInboundEventMapper;
import org.jeecg.modules.airag.collaboration.notification.FeishuDeliveryService;
import org.jeecg.modules.airag.collaboration.feishu.FeishuCardSigner;
import org.jeecg.modules.airag.collaboration.mapper.AiFeishuSessionMapper;
import org.jeecg.modules.airag.collaboration.service.*;
import org.jeecg.modules.airag.collaboration.session.FeishuSessionService;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.*;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.validation.PipelineException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class FeishuInboundCommandProcessor implements FeishuInboundEventProcessor {
    private final AiFeishuInboundEventMapper eventMapper;
    private final AiFeishuBotMapper botMapper;
    private final SecretCipherService cipher;
    private final ObjectMapper mapper;
    private final FeishuCommandParser parser;
    private final FeishuBindingService bindings;
    private final FeishuAccessService access;
    private final FeishuSessionService sessions;
    private final RunInterventionService interventions;
    private final RunNotificationViewProvider runViews;
    private final FeishuDeliveryService deliveries;
    private final FeishuRunCommandService runCommands;
    private final FeishuCardSigner cardSigner;
    private final AiFeishuSessionMapper sessionMapper;

    public FeishuInboundCommandProcessor(AiFeishuInboundEventMapper eventMapper, AiFeishuBotMapper botMapper,
            SecretCipherService cipher, ObjectMapper mapper, FeishuCommandParser parser,
            FeishuBindingService bindings, FeishuAccessService access, FeishuSessionService sessions,
            RunInterventionService interventions, RunNotificationViewProvider runViews,
            FeishuDeliveryService deliveries, FeishuRunCommandService runCommands,
            FeishuCardSigner cardSigner, AiFeishuSessionMapper sessionMapper) {
        this.eventMapper=eventMapper;this.botMapper=botMapper;this.cipher=cipher;this.mapper=mapper;this.parser=parser;
        this.bindings=bindings;this.access=access;this.sessions=sessions;
        this.interventions=interventions;this.runViews=runViews;this.deliveries=deliveries;
        this.runCommands=runCommands;
        this.cardSigner=cardSigner;this.sessionMapper=sessionMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(String inboundEventId) {
        // Session row locks and Run/intervention changes must share one transaction for thread-level serialization.
        AiFeishuInboundEvent event = eventMapper.selectById(inboundEventId);
        if (event == null || !StringUtils.hasText(event.getPayloadCipher())) return;
        if (InboundEventType.CARD_ACTION.name().equals(event.getEventType())) {
            processCard(event); return;
        }
        JsonNode payload = read(cipher.decrypt(event.getPayloadCipher()));
        AiFeishuBot bot = botMapper.selectById(event.getBotId());
        if (bot == null || !Boolean.TRUE.equals(bot.getEnabled()) || !Boolean.TRUE.equals(bot.getCommandEnabled())) {
            complete(event, "IGNORED", null, null); return;
        }
        if ("bot".equalsIgnoreCase(text(payload, "senderType")) || !"text".equals(text(payload, "messageType"))) {
            complete(event, "IGNORED", null, null); return;
        }
        String rawText = parser.extractText(text(payload, "content"));
        String chatId = text(payload, "chatId"); String messageId = text(payload, "messageId");
        String threadKey = first(text(payload, "rootId"), text(payload, "threadId"), messageId);
        try {
            Command command = parser.parse(rawText, text(payload, "chatType"), strings(payload.path("mentionKeys")));
            if (CommandType.HELP.name().equals(command.type())) {
                reply(event, bot, messageId, "HELP", "使用【流程代码或别名】，任务；角色【Agent代码】，任务；绑定【绑定码】");
                complete(event, "HELP", null, null); return;
            }
            if (CommandType.BIND.name().equals(command.type())) {
                bindings.consumeToken(bot.getId(), event.getSenderOpenId(), command.key());
                reply(event, bot, messageId, "BINDING_USED", "绑定成功");
                complete(event, "BINDING_USED", null, null); return;
            }
            AiFeishuUserBinding binding = bindings.requireEnabled(bot.getId(), event.getSenderOpenId());
            AgentAccessContext context = new AgentAccessContext(binding.getUsername(), binding.getTenantId());
            access.requireBot(bot.getId(), context, true);
            AiFeishuSession active = sessions.lockActive(binding.getTenantId(), bot.getId(), chatId, threadKey);
            if (active != null && StringUtils.hasText(event.getRunId()) && event.getRunId().equals(active.getRunId())) {
                // A crash after the transaction commit but before Inbox cleanup must not resume or recreate the same Run.
                complete(event, "RUN_CREATED", active.getRunId(), null);
                return;
            }
            if (active != null && SessionStatus.WAITING.name().equals(active.getStatus())) {
                supplyInput(event, bot, active, binding, rawText, messageId, context);
                return;
            }
            if (active != null) throw CollaborationException.conflict("FEISHU_SESSION_CONFLICT", "This thread already has an active run");
            RunCreateResult created = runCommands.startAndBind(event, bot, command, rawText, chatId, threadKey,
                    messageId, binding, context);
            complete(event, "RUN_CREATED", created.runId(), null);
        } catch (CollaborationException | PipelineException | ExecutionException | JeecgBootException deterministic) {
            reply(event, bot, messageId, "COMMAND_REJECTED", safe(deterministic.getMessage()));
            complete(event, "REJECTED", null, null);
        }
    }

    private void processCard(AiFeishuInboundEvent event) {
        JsonNode payload=read(cipher.decrypt(event.getPayloadCipher()));AiFeishuBot bot=botMapper.selectById(event.getBotId());
        try{if(bot==null||!Boolean.TRUE.equals(bot.getEnabled())||!Boolean.TRUE.equals(bot.getCommandEnabled()))throw CollaborationException.notFound("FEISHU_BOT_NOT_AVAILABLE","Feishu bot is not available");
            if(!payload.path("actionValue").isObject()||!cardSigner.verify((ObjectNode)payload.path("actionValue")))throw CollaborationException.badRequest("FEISHU_CARD_SIGNATURE_INVALID","Card action is invalid");
            ObjectNode value=(ObjectNode)payload.path("actionValue");String runId=value.path("runId").asText();String interventionId=value.path("interventionId").asText();String resumeToken=value.path("resumeToken").asText();InterventionAction action=InterventionAction.valueOf(value.path("action").asText());
            AiFeishuUserBinding binding=bindings.requireEnabled(bot.getId(),event.getSenderOpenId());AgentAccessContext context=new AgentAccessContext(binding.getUsername(),binding.getTenantId());
            access.requirePermissions(context,"ai:feishu:list",action==InterventionAction.CANCEL?"ai:run:cancel":"ai:run:intervene");
            AiFeishuSession session=sessionMapper.selectByRunIdForUpdate(runId);if(session==null||!bot.getId().equals(session.getBotId())||!binding.getId().equals(session.getBindingId())||!event.getSenderOpenId().equals(session.getSenderOpenId()))throw CollaborationException.notFound("FEISHU_INTERVENTION_NOT_AVAILABLE","Intervention is not available");
            RunNotificationViewProvider.RunCollaborationState state=runViews.state(runId,context);if(state.openIntervention()==null||!interventionId.equals(state.openIntervention().id())||!resumeToken.equals(state.openIntervention().resumeToken()))throw CollaborationException.notFound("FEISHU_INTERVENTION_NOT_AVAILABLE","Intervention is not available");
            InterventionResolveRequest request=new InterventionResolveRequest();request.setRequestId("fs-int:"+event.getId());request.setSourceMessageId(event.getId());request.setAction(action);
            if(action==InterventionAction.SUPPLY_INPUT){String supplied=payload.path("formValue").path("text").asText(payload.path("inputValue").asText());if(!StringUtils.hasText(supplied))throw CollaborationException.badRequest("FEISHU_COMMAND_INVALID","Supplemental input is required");ObjectNode resume=mapper.createObjectNode();resume.put("text",supplied);request.setResumeInput(resume);}
            interventions.resolve(runId,interventionId,request,context);sessions.updateStatus(session,action==InterventionAction.CANCEL?SessionStatus.CANCELED:SessionStatus.ACTIVE,event.getMessageId());reply(event,bot,event.getMessageId(),action==InterventionAction.CANCEL?"RUN_CANCELED":"RUN_RESUMED","操作已处理");complete(event,"INTERVENTION_RESOLVED",runId,interventionId);
        }catch(CollaborationException|ExecutionException|IllegalArgumentException deterministic){if(bot!=null&&StringUtils.hasText(event.getMessageId()))reply(event,bot,event.getMessageId(),"CARD_REJECTED",safe(deterministic.getMessage()));complete(event,"REJECTED",null,null);}
    }

    private void supplyInput(AiFeishuInboundEvent event, AiFeishuBot bot, AiFeishuSession session,
                             AiFeishuUserBinding binding, String rawText, String messageId, AgentAccessContext context) {
        if (!binding.getId().equals(session.getBindingId()) || !event.getSenderOpenId().equals(session.getSenderOpenId()))
            throw CollaborationException.notFound("FEISHU_INTERVENTION_NOT_AVAILABLE", "Intervention is not available");
        access.requirePermissions(context, "ai:feishu:list", "ai:run:intervene");
        RunNotificationViewProvider.RunCollaborationState state = runViews.state(session.getRunId(), context);
        if (state.status() != RunStatus.WAITING || state.openIntervention() == null)
            throw CollaborationException.notFound("FEISHU_INTERVENTION_NOT_AVAILABLE", "Intervention is not available");
        InterventionResolveRequest request = new InterventionResolveRequest();
        request.setRequestId("fs-int:" + event.getId()); request.setSourceMessageId(event.getId());
        request.setAction(InterventionAction.SUPPLY_INPUT); ObjectNode resume = mapper.createObjectNode();
        resume.put("text", rawText); request.setResumeInput(resume);
        interventions.resolve(session.getRunId(), state.openIntervention().id(), request, context);
        sessions.updateStatus(session, SessionStatus.ACTIVE, messageId);
        reply(event, bot, session.getRootMessageId(), "RUN_RESUMED", "补充信息已接收，运行继续");
        complete(event, "INTERVENTION_RESOLVED", session.getRunId(), state.openIntervention().id());
    }

    private void reply(AiFeishuInboundEvent event, AiFeishuBot bot, String target, String type, String message) {
        ObjectNode content = mapper.createObjectNode(); content.put("text", message);
        deliveries.enqueue(event.getId(), event.getTenantId(), event.getRunId(), type, null, null, bot.getId(),
                DeliveryTargetType.REPLY, target, DeliveryMessageType.TEXT, content);
    }

    private void complete(AiFeishuInboundEvent event, String type, String runId, String interventionId) {
        event.setResultType(type); event.setRunId(runId); event.setInterventionId(interventionId); event.setUpdateTime(new Date());
        eventMapper.updateById(event);
    }

    private JsonNode read(String json) { try { return mapper.readTree(json); } catch (Exception e) { throw new IllegalArgumentException("Invalid Inbox payload"); } }
    private String text(JsonNode node, String field) { return node.path(field).isNull() ? null : node.path(field).asText(null); }
    private List<String> strings(JsonNode node) { List<String> values=new ArrayList<>(); if(node.isArray())node.forEach(v->values.add(v.asText()));return values; }
    private String first(String... values) { for(String value:values)if(StringUtils.hasText(value))return value;return null; }
    private String safe(String value) { return StringUtils.hasText(value) ? (value.length()>500?value.substring(0,500):value) : "请求无法处理"; }
}
