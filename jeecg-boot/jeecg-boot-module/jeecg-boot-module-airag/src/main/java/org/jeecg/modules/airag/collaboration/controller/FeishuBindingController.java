package org.jeecg.modules.airag.collaboration.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.*;
import org.jeecg.modules.airag.collaboration.service.FeishuBindingService;
import org.jeecg.modules.airag.pipeline.service.PipelineAccessContextFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/feishu-bindings")
public class FeishuBindingController {
    private final FeishuBindingService service;
    private final PipelineAccessContextFactory contexts;

    public FeishuBindingController(FeishuBindingService service, PipelineAccessContextFactory contexts) {
        this.service = service; this.contexts = contexts;
    }

    @GetMapping
    @RequiresPermissions("ai:feishu:binding:list")
    public Result<IPage<BindingView>> page(@RequestParam String botId,
                                           @RequestParam(required = false) String username,
                                           @RequestParam(required = false) Boolean enabled,
                                           @RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return Result.OK(service.page(botId, username, enabled, pageNo, pageSize, contexts.current()));
    }

    @PostMapping
    @RequiresPermissions("ai:feishu:binding:add")
    public Result<String> create(@Valid @RequestBody BindingCreateRequest request) {
        return Result.OK(service.createAdmin(request, contexts.current()));
    }

    @PostMapping("/{id}/disable")
    @RequiresPermissions("ai:feishu:binding:disable")
    public Result<String> disable(@PathVariable String id) {
        service.disable(id, contexts.current()); return Result.OK("Disabled");
    }

    @PostMapping("/token")
    @RequiresPermissions("ai:feishu:binding:self")
    public Result<BindingTokenResult> token(@Valid @RequestBody BindingTokenRequest request) {
        return Result.OK(service.createToken(request.getBotId(), contexts.current()));
    }
}
