package org.jeecg.modules.airag.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.aspect.annotation.PermissionData;
import org.jeecg.modules.airag.agent.dto.AiConfigDtos;
import org.jeecg.modules.airag.agent.service.IAiConnectorService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/ai/connectors")
@Tag(name = "Multi-Agent configuration - HTTP Connectors")
public class AiConnectorController {
    private static final String COMPONENT = "super/multiagent/connector/AiConnectorList";
    private final IAiConnectorService service;

    public AiConnectorController(IAiConnectorService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("ai:connector:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<IPage<AiConfigDtos.ConnectorView>> list(
            @RequestParam(required = false) String connectorCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) Integer pageSize) {
        return Result.OK(service.pageViews(connectorCode, name, enabled, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("ai:connector:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<AiConfigDtos.ConnectorView> get(@PathVariable String id) {
        return Result.OK(service.getView(id));
    }

    @PostMapping
    @RequiresPermissions("ai:connector:add")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Create HTTP Connector")
    public Result<String> create(@Valid @RequestBody AiConfigDtos.ConnectorUpsertRequest request) {
        return Result.OK(service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermissions("ai:connector:edit")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Update HTTP Connector")
    public Result<String> update(@PathVariable String id, @Valid @RequestBody AiConfigDtos.ConnectorUpsertRequest request) {
        service.update(id, request);
        return Result.OK("Updated");
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("ai:connector:delete")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Delete HTTP Connector")
    public Result<String> delete(@PathVariable String id) {
        service.delete(id);
        return Result.OK("Deleted");
    }

    @PostMapping("/{id}/enable")
    @RequiresPermissions("ai:connector:enable")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Enable HTTP Connector")
    public Result<String> enable(@PathVariable String id) {
        service.enable(id);
        return Result.OK("Enabled");
    }

    @PostMapping("/{id}/disable")
    @RequiresPermissions("ai:connector:disable")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Disable HTTP Connector")
    public Result<String> disable(@PathVariable String id) {
        service.disable(id);
        return Result.OK("Disabled");
    }

    @PostMapping("/{id}/test")
    @RequiresPermissions("ai:connector:test")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Test HTTP Connector")
    public Result<AiConfigDtos.ConnectionTestResult> test(@PathVariable String id,
            @RequestBody(required = false) AiConfigDtos.TestInput request) {
        return Result.OK(service.test(id, request == null ? null : request.getInput()));
    }
}
