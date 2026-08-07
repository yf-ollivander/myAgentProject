package org.jeecg.modules.airag.collaboration.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.modules.airag.agent.dto.AgentConfigSnapshot;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.entity.AiFeishuBot;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.agent.service.AuthorizedAgentConfigProvider;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.CommandType;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.Command;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuInboundEvent;
import org.jeecg.modules.airag.collaboration.entity.AiFeishuUserBinding;
import org.jeecg.modules.airag.collaboration.service.CollaborationException;
import org.jeecg.modules.airag.collaboration.service.FeishuAccessService;
import org.jeecg.modules.airag.collaboration.session.FeishuSessionService;
import org.jeecg.modules.airag.collaboration.notification.FeishuDeliveryService;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.DeliveryMessageType;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.DeliveryTargetType;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.*;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.*;
import org.jeecg.modules.airag.execution.service.RunStartService;
import org.jeecg.modules.airag.pipeline.contract.PublishedPipelineSnapshot;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FeishuRunCommandService {
    private final FeishuAccessService access; private final AuthorizedPipelineResolver pipelines;
    private final AuthorizedAgentConfigProvider agents; private final RunStartService runs;
    private final FeishuSessionService sessions; private final ObjectMapper mapper;
    private final FeishuDeliveryService deliveries;

    public FeishuRunCommandService(FeishuAccessService access, AuthorizedPipelineResolver pipelines,
            AuthorizedAgentConfigProvider agents, RunStartService runs, FeishuSessionService sessions,
            ObjectMapper mapper, FeishuDeliveryService deliveries) {
        this.access=access;this.pipelines=pipelines;this.agents=agents;this.runs=runs;this.sessions=sessions;this.mapper=mapper;
        this.deliveries=deliveries;
    }

    @Transactional(rollbackFor = Exception.class)
    public RunCreateResult startAndBind(AiFeishuInboundEvent event, AiFeishuBot bot, Command command,
            String rawText, String chatId, String threadKey, String messageId,
            AiFeishuUserBinding binding, AgentAccessContext context) {
        access.requirePermissions(context, "ai:feishu:list", "ai:run:start");
        RunCreateRequest request = new RunCreateRequest(); request.setRequestId("fs-run:" + event.getId());
        String task;
        if (AiConfigDtos.DIRECT_AGENT.equals(bot.getEntryMode())) {
            AgentConfigSnapshot snapshot = agents.resolveEnabledSnapshotByBotId(bot.getId(), context);
            task = rawText.trim(); request.setRunType(RunType.AGENT_DIRECT); request.setAgentId(snapshot.getAgentId());
        } else if (CommandType.PIPELINE.name().equals(command.type())) {
            PublishedPipelineSnapshot snapshot = pipelines.resolveEnabledByTriggerKey(bot.getId(), command.key(), context);
            task = command.task(); request.setRunType(RunType.PIPELINE); request.setPipelineId(snapshot.getPipelineId());
        } else if (CommandType.AGENT.name().equals(command.type())) {
            AgentConfigSnapshot snapshot = agents.resolveEnabledSnapshotByCode(command.key(), context);
            task = command.task(); request.setRunType(RunType.AGENT_DIRECT); request.setAgentId(snapshot.getAgentId());
        } else throw CollaborationException.badRequest("FEISHU_COMMAND_INVALID", "Command is invalid");
        if (!StringUtils.hasText(task) || task.codePointCount(0, task.length()) > 10000)
            throw CollaborationException.badRequest("FEISHU_COMMAND_INVALID", "Task is invalid");
        ObjectNode input = mapper.createObjectNode(); input.put("task", task); request.setInput(input);
        RunSourceContext source = new RunSourceContext(RunSource.FEISHU, event.getId(), bot.getId(), chatId, threadKey);
        RunCreateResult created = request.getRunType() == RunType.PIPELINE
                ? runs.startPipeline(request, context, source) : runs.startDirectAgent(request, context, source);
        sessions.create(binding.getTenantId(), bot.getId(), chatId, threadKey, messageId, created.runId(),
                binding.getId(), event.getSenderOpenId(), messageId);
        ObjectNode accepted = mapper.createObjectNode(); accepted.put("text", "运行已受理，runId=" + created.runId());
        // Sequence zero keeps command acknowledgement ahead of all run business milestones.
        deliveries.enqueue(event.getId(), binding.getTenantId(), created.runId(), "COMMAND_ACCEPTED", 0L, null,
                bot.getId(), DeliveryTargetType.REPLY, messageId, DeliveryMessageType.TEXT, accepted);
        return created;
    }
}
