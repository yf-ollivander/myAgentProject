package org.jeecg.modules.airag.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.aspect.annotation.PermissionData;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.service.IAiAgentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/ai/agents")
@Tag(name = "Multi-Agent configuration - Agents")
public class AiAgentController {
    private static final String COMPONENT = "super/multiagent/agent/AiAgentList";
    private final IAiAgentService service;
    private final ObjectMapper objectMapper;

    public AiAgentController(IAiAgentService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @RequiresPermissions("ai:agent:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<IPage<AiConfigDtos.AgentView>> list(
            @RequestParam(required = false) String agentCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) Integer pageSize) {
        return Result.OK(service.pageViews(agentCode, name, enabled, pageNo, pageSize));
    }

    @GetMapping("/options")
    @RequiresPermissions("ai:agent:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<List<AiConfigDtos.AgentOption>> options(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) Integer limit) {
        return Result.OK(service.listVisibleOptions(keyword, limit));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("ai:agent:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<AiConfigDtos.AgentView> get(@PathVariable String id) {
        return Result.OK(service.getView(id));
    }

    @PostMapping
    @RequiresPermissions("ai:agent:add")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Create Agent configuration")
    public Result<String> create(@Valid @RequestBody AiConfigDtos.AgentUpsertRequest request) {
        return Result.OK(service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermissions("ai:agent:edit")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Update Agent configuration")
    public Result<String> update(@PathVariable String id, @Valid @RequestBody AiConfigDtos.AgentUpsertRequest request) {
        service.update(id, request);
        return Result.OK("Updated");
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("ai:agent:delete")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Delete Agent configuration")
    public Result<String> delete(@PathVariable String id) {
        service.delete(id);
        return Result.OK("Deleted");
    }

    @PostMapping("/{id}/enable")
    @RequiresPermissions("ai:agent:enable")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Enable Agent configuration")
    public Result<String> enable(@PathVariable String id) {
        service.enable(id);
        return Result.OK("Enabled");
    }

    @PostMapping("/{id}/disable")
    @RequiresPermissions("ai:agent:disable")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Disable Agent configuration")
    public Result<String> disable(@PathVariable String id) {
        service.disable(id);
        return Result.OK("Disabled");
    }

    @PostMapping("/{id}/test")
    @RequiresPermissions("ai:agent:test")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Test Agent configuration")
    public Result<AiConfigDtos.ConnectionTestResult> test(@PathVariable String id,
            @RequestBody(required = false) AiConfigDtos.TestInput request) {
        // Keep Agent tests on the same Jackson 3-to-2 boundary as direct Connector tests.
        return Result.OK(service.test(id, request == null ? null : request.toInternalJson(objectMapper)));
    }
}
