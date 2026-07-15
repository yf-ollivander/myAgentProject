package org.jeecg.modules.airag.pipeline.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.aspect.annotation.PermissionData;
import org.jeecg.modules.airag.pipeline.dto.PipelineDtos;
import org.jeecg.modules.airag.pipeline.service.AuthorizedPipelineBotResolver;
import org.jeecg.modules.airag.pipeline.service.IAiPipelineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/ai/pipelines")
@Tag(name = "Multi-Agent pipelines")
public class AiPipelineController {
    public static final String COMPONENT = "super/multiagent/pipeline/AiPipelineList";
    private final IAiPipelineService service;
    private final AuthorizedPipelineBotResolver botResolver;

    public AiPipelineController(IAiPipelineService service, AuthorizedPipelineBotResolver botResolver) {
        this.service = service;
        this.botResolver = botResolver;
    }

    @GetMapping
    @RequiresPermissions("ai:pipeline:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<IPage<PipelineDtos.ListItem>> list(@RequestParam(required = false) String code,
            @RequestParam(required = false) String name, @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize) {
        return Result.OK(service.page(code, name, enabled, pageNo, pageSize));
    }

    @PostMapping
    @RequiresPermissions("ai:pipeline:add")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Create multi-Agent pipeline")
    public Result<PipelineDtos.CreateResult> create(@Valid @RequestBody PipelineDtos.CreateRequest request) {
        return Result.OK(service.create(request));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("ai:pipeline:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<PipelineDtos.Detail> get(@PathVariable String id) {
        return Result.OK(service.get(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("ai:pipeline:delete")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Delete multi-Agent pipeline")
    public Result<String> delete(@PathVariable String id) {
        service.delete(id);
        return Result.OK("Deleted");
    }

    @GetMapping("/{id}/draft")
    @RequiresPermissions("ai:pipeline:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<PipelineDtos.DraftView> draft(@PathVariable String id) {
        return Result.OK(service.getDraft(id));
    }

    @PutMapping("/{id}/draft")
    @RequiresPermissions("ai:pipeline:edit")
    @PermissionData(pageComponent = COMPONENT)
    public Result<Long> saveDraft(@PathVariable String id,
                                  @Valid @RequestBody PipelineDtos.DraftSaveRequest request) {
        return Result.OK(service.saveDraft(id, request));
    }

    @PostMapping("/{id}/validate")
    @RequiresPermissions("ai:pipeline:validate")
    @PermissionData(pageComponent = COMPONENT)
    public Result<PipelineDtos.ValidationResult> validate(@PathVariable String id,
            @Valid @RequestBody PipelineDtos.RevisionRequest request) {
        return Result.OK(service.validate(id, request.getDraftRevision()));
    }

    @PostMapping("/{id}/publish")
    @RequiresPermissions("ai:pipeline:publish")
    @PermissionData(pageComponent = COMPONENT)
    public Result<PipelineDtos.PublishResult> publish(@PathVariable String id,
            @Valid @RequestBody PipelineDtos.PublishRequest request) {
        return Result.OK(service.publish(id, request));
    }

    @GetMapping("/{id}/versions")
    @RequiresPermissions("ai:pipeline:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<IPage<PipelineDtos.VersionSummary>> versions(@PathVariable String id,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize) {
        return Result.OK(service.versions(id, pageNo, pageSize));
    }

    @GetMapping("/{id}/versions/{version}")
    @RequiresPermissions("ai:pipeline:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<PipelineDtos.VersionView> version(@PathVariable String id, @PathVariable int version) {
        return Result.OK(service.version(id, version));
    }

    @PostMapping("/{id}/enable")
    @RequiresPermissions("ai:pipeline:enable")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Enable multi-Agent pipeline")
    public Result<String> enable(@PathVariable String id) {
        service.enable(id);
        return Result.OK("Enabled");
    }

    @PostMapping("/{id}/disable")
    @RequiresPermissions("ai:pipeline:disable")
    @PermissionData(pageComponent = COMPONENT)
    @AutoLog("Disable multi-Agent pipeline")
    public Result<String> disable(@PathVariable String id) {
        service.disable(id);
        return Result.OK("Disabled");
    }

    @GetMapping("/options")
    @RequiresPermissions("ai:pipeline:list")
    @PermissionData(pageComponent = COMPONENT)
    public Result<List<PipelineDtos.PipelineOption>> options(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return Result.OK(service.options(keyword, limit));
    }

    @GetMapping("/notification-bot-options")
    @RequiresPermissions({"ai:pipeline:edit", "ai:feishu:list"})
    @PermissionData(pageComponent = COMPONENT)
    public Result<List<AuthorizedPipelineBotResolver.BotOption>> botOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return Result.OK(botResolver.listVisibleOptions(keyword, limit));
    }
}
