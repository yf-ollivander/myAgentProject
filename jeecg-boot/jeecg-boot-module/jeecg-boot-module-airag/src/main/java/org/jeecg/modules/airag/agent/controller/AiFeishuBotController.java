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
import org.jeecg.modules.airag.agent.service.IAiFeishuBotService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/ai/feishu-bots")
@Tag(name = "Multi-Agent configuration - Feishu bots")
public class AiFeishuBotController {
    private static final String COMPONENT = "super/multiagent/feishu/AiFeishuBotList";
    private final IAiFeishuBotService service;

    public AiFeishuBotController(IAiFeishuBotService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("ai:feishu:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<IPage<AiConfigDtos.FeishuBotView>> list(
            @RequestParam(required = false) String botKey,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) Integer pageSize) {
        return Result.OK(service.pageViews(botKey, name, enabled, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("ai:feishu:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<AiConfigDtos.FeishuBotView> get(@PathVariable String id) {
        return Result.OK(service.getView(id));
    }

    @PostMapping
    @RequiresPermissions("ai:feishu:add")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Create Feishu bot")
    public Result<String> create(@Valid @RequestBody AiConfigDtos.FeishuBotUpsertRequest request) {
        return Result.OK(service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermissions("ai:feishu:edit")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Update Feishu bot")
    public Result<String> update(@PathVariable String id, @Valid @RequestBody AiConfigDtos.FeishuBotUpsertRequest request) {
        service.update(id, request);
        return Result.OK("Updated");
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("ai:feishu:delete")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Delete Feishu bot")
    public Result<String> delete(@PathVariable String id) {
        service.delete(id);
        return Result.OK("Deleted");
    }

    @PostMapping("/{id}/enable")
    @RequiresPermissions("ai:feishu:enable")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Enable Feishu bot")
    public Result<String> enable(@PathVariable String id) {
        service.enable(id);
        return Result.OK("Enabled");
    }

    @PostMapping("/{id}/disable")
    @RequiresPermissions("ai:feishu:disable")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Disable Feishu bot")
    public Result<String> disable(@PathVariable String id) {
        service.disable(id);
        return Result.OK("Disabled");
    }

    @PostMapping("/{id}/test")
    @RequiresPermissions("ai:feishu:test")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Test Feishu bot")
    public Result<AiConfigDtos.ConnectionTestResult> test(@PathVariable String id,
            @Valid @RequestBody(required = false) AiConfigDtos.FeishuTestInput request) {
        return Result.OK(service.test(id, request == null ? null : request.getTestMessage()));
    }
}
