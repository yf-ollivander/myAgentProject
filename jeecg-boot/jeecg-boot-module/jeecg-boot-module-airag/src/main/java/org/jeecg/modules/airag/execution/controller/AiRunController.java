package org.jeecg.modules.airag.execution.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.PermissionData;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.execution.contract.ExecutionEnums.RunType;
import org.jeecg.modules.airag.execution.dto.ExecutionDtos.*;
import org.jeecg.modules.airag.execution.service.*;
import org.jeecg.modules.airag.pipeline.service.PipelineAccessContextFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Validated @RestController @RequestMapping("/api/ai/runs")
public class AiRunController {
    public static final String COMPONENT="super/multiagent/run/AiRunList";
    private final RunStartService starts;private final RunOperationService operations;private final RunQueryService queries;private final PipelineAccessContextFactory contexts;
    public AiRunController(RunStartService starts,RunOperationService operations,RunQueryService queries,PipelineAccessContextFactory contexts){this.starts=starts;this.operations=operations;this.queries=queries;this.contexts=contexts;}
    @GetMapping @RequiresPermissions("ai:run:list") @PermissionData(pageComponent=COMPONENT)
    public Result<IPage<RunListItem>> list(@RequestParam(required=false)String status,@RequestParam(required=false)String runType,
            @RequestParam(required=false)String pipelineId,@RequestParam(required=false)@DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")Date from,
            @RequestParam(required=false)@DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")Date to,@RequestParam(defaultValue="1")@Min(1)int pageNo,
            @RequestParam(defaultValue="10")@Min(1)@Max(200)int pageSize){return Result.OK(queries.page(status,runType,pipelineId,from,to,pageNo,pageSize,contexts.current()));}
    @PostMapping @RequiresPermissions("ai:run:start")
    public Result<RunCreateResult> start(@Valid@RequestBody RunCreateRequest request){AgentAccessContext context=contexts.current();return Result.OK(request.getRunType()==RunType.PIPELINE?starts.startPipeline(request,context,RunSourceContext.jeecg()):starts.startDirectAgent(request,context,RunSourceContext.jeecg()));}
    @GetMapping("/{id}") @RequiresPermissions("ai:run:list") @PermissionData(pageComponent=COMPONENT)
    public Result<RunDetail> detail(@PathVariable String id){return Result.OK(queries.detail(id,contexts.current()));}
    @GetMapping("/{id}/events") @RequiresPermissions("ai:run:list") @PermissionData(pageComponent=COMPONENT)
    public Result<List<EventView>> events(@PathVariable String id){return Result.OK(queries.events(id,contexts.current()));}
    @PostMapping("/{id}/cancel") @RequiresPermissions("ai:run:cancel")
    public Result<String> cancel(@PathVariable String id,@Valid@RequestBody RunCancelRequest request){operations.cancel(id,request,contexts.current());return Result.OK("Canceled");}
    @PostMapping("/{id}/retry") @RequiresPermissions("ai:run:retry")
    public Result<RunCreateResult> retry(@PathVariable String id,@Valid@RequestBody RunRetryRequest request){return Result.OK(operations.retry(id,request,contexts.current()));}
    @GetMapping("/{id}/interventions/open") @RequiresPermissions("ai:run:list") @PermissionData(pageComponent=COMPONENT)
    public Result<InterventionView> intervention(@PathVariable String id){return Result.OK(queries.openIntervention(id,contexts.current()));}
    @PostMapping("/{id}/interventions/{interventionId}/resolve") @RequiresPermissions("ai:run:intervene")
    public Result<String> resolve(@PathVariable String id,@PathVariable String interventionId,@Valid@RequestBody InterventionResolveRequest request){operations.resolve(id,interventionId,request,contexts.current());return Result.OK("Resolved");}
    @GetMapping("/{id}/artifacts") @RequiresPermissions("ai:run:list") @PermissionData(pageComponent=COMPONENT)
    public Result<List<ArtifactView>> artifacts(@PathVariable String id){return Result.OK(queries.artifacts(id,contexts.current()));}
    @GetMapping("/{id}/summary") @RequiresPermissions("ai:run:list") @PermissionData(pageComponent=COMPONENT)
    public Result<RunSummary> summary(@PathVariable String id){return Result.OK(queries.summary(id,contexts.current()));}
}
