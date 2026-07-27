$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$script:WordNamespace = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
$script:XmlNamespace = 'http://www.w3.org/XML/1998/namespace'

function New-WordElement {
    param([System.Xml.XmlDocument]$Document, [string]$LocalName)
    return $Document.CreateElement('w', $LocalName, $script:WordNamespace)
}

function Set-WordAttribute {
    param([System.Xml.XmlElement]$Element, [string]$Name, [string]$Value)
    $attribute = $Element.OwnerDocument.CreateAttribute('w', $Name, $script:WordNamespace)
    $attribute.Value = $Value
    [void]$Element.Attributes.Append($attribute)
}

function New-WordParagraph {
    param(
        [System.Xml.XmlDocument]$Document,
        [string]$Text,
        [string]$Style = 'Normal',
        [switch]$PageBreak
    )

    $paragraph = New-WordElement $Document 'p'
    if ($Style) {
        $properties = New-WordElement $Document 'pPr'
        $paragraphStyle = New-WordElement $Document 'pStyle'
        Set-WordAttribute $paragraphStyle 'val' $Style
        [void]$properties.AppendChild($paragraphStyle)
        [void]$paragraph.AppendChild($properties)
    }

    if ($PageBreak) {
        $breakRun = New-WordElement $Document 'r'
        $break = New-WordElement $Document 'br'
        Set-WordAttribute $break 'type' 'page'
        [void]$breakRun.AppendChild($break)
        [void]$paragraph.AppendChild($breakRun)
    }

    $run = New-WordElement $Document 'r'
    $textNode = New-WordElement $Document 't'
    $spaceAttribute = $Document.CreateAttribute('xml', 'space', $script:XmlNamespace)
    $spaceAttribute.Value = 'preserve'
    [void]$textNode.Attributes.Append($spaceAttribute)
    $textNode.InnerText = $Text
    [void]$run.AppendChild($textNode)
    [void]$paragraph.AppendChild($run)
    return $paragraph
}

function New-WordTable {
    param(
        [System.Xml.XmlDocument]$Document,
        [string[]]$Headers,
        [object[]]$Rows
    )

    $table = New-WordElement $Document 'tbl'
    $tableProperties = New-WordElement $Document 'tblPr'
    $tableStyle = New-WordElement $Document 'tblStyle'
    Set-WordAttribute $tableStyle 'val' 'TableGrid'
    [void]$tableProperties.AppendChild($tableStyle)
    [void]$table.AppendChild($tableProperties)

    # Build the header separately so PowerShell does not flatten the string array
    # into several one-cell rows when composing heterogeneous table data.
    $headerRow = New-WordElement $Document 'tr'
    foreach ($value in $Headers) {
        $cell = New-WordElement $Document 'tc'
        [void]$cell.AppendChild((New-WordParagraph $Document ([string]$value)))
        [void]$headerRow.AppendChild($cell)
    }
    [void]$table.AppendChild($headerRow)

    foreach ($rowValues in $Rows) {
        $row = New-WordElement $Document 'tr'
        foreach ($value in $rowValues) {
            $cell = New-WordElement $Document 'tc'
            [void]$cell.AppendChild((New-WordParagraph $Document ([string]$value)))
            [void]$row.AppendChild($cell)
        }
        [void]$table.AppendChild($row)
    }
    return $table
}

function H1([string]$Text) { return [pscustomobject]@{ Type = 'Heading1'; Text = $Text } }
function H2([string]$Text) { return [pscustomobject]@{ Type = 'Heading2'; Text = $Text } }
function P([string]$Text) { return [pscustomobject]@{ Type = 'Paragraph'; Text = $Text } }
function T([string[]]$Headers, [object[]]$Rows) { return [pscustomobject]@{ Type = 'Table'; Headers = $Headers; Rows = $Rows } }

function Add-RevisionAppendix {
    param(
        [string]$SourcePath,
        [string]$DestinationPath,
        [string]$RevisionNotice,
        [object[]]$Blocks
    )

    # Preserve the implemented plan as evidence. Each revision is a new file, and the
    # appended chapter explicitly overrides conflicting legacy text instead of silently
    # rewriting historical decisions.
    Copy-Item -LiteralPath $SourcePath -Destination $DestinationPath -Force
    $archive = [System.IO.Compression.ZipFile]::Open($DestinationPath, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        $entry = $archive.GetEntry('word/document.xml')
        $readStream = $entry.Open()
        try {
            $document = New-Object System.Xml.XmlDocument
            $document.PreserveWhitespace = $true
            $document.Load($readStream)
        } finally {
            $readStream.Dispose()
        }

        $namespaceManager = New-Object System.Xml.XmlNamespaceManager($document.NameTable)
        $namespaceManager.AddNamespace('w', $script:WordNamespace)
        $body = $document.SelectSingleNode('//w:body', $namespaceManager)
        $sectionProperties = $body.SelectSingleNode('./w:sectPr', $namespaceManager)

        $notice = New-WordParagraph $document $RevisionNotice 'Normal'
        if ($body.FirstChild) {
            [void]$body.InsertAfter($notice, $body.FirstChild)
        } else {
            [void]$body.AppendChild($notice)
        }

        $first = $true
        foreach ($block in $Blocks) {
            switch ($block.Type) {
                'Heading1' {
                    $node = New-WordParagraph $document $block.Text 'Heading1' -PageBreak:$first
                    $first = $false
                }
                'Heading2' { $node = New-WordParagraph $document $block.Text 'Heading2' }
                'Paragraph' { $node = New-WordParagraph $document $block.Text 'Normal' }
                'Table' { $node = New-WordTable $document $block.Headers $block.Rows }
                default { throw "Unknown block type: $($block.Type)" }
            }

            if ($sectionProperties) {
                [void]$body.InsertBefore($node, $sectionProperties)
            } else {
                [void]$body.AppendChild($node)
            }
        }

        $writeStream = $entry.Open()
        try {
            $writeStream.SetLength(0)
            $settings = New-Object System.Xml.XmlWriterSettings
            $settings.Encoding = New-Object System.Text.UTF8Encoding($false)
            $settings.Indent = $false
            $writer = [System.Xml.XmlWriter]::Create($writeStream, $settings)
            try { $document.Save($writer) } finally { $writer.Dispose() }
        } finally {
            $writeStream.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
}

$outputDirectory = Join-Path $PSScriptRoot 'revised-docx'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$overallBlocks = @(
    (H1 '14. V3.1 飞书协作流水线初版强制修订'),
    (P '本章是对 V3.0 的强制修订。若与前文冲突，以本章及 01 至 05 修订版为准。初版目标由“可触发单 Agent 或简单流程”提升为“可从飞书选择已发布流水线或角色，驱动 3 至 5 个角色串行协作，保存每步产出，在阻塞时等待用户并从原运行恢复，最终汇总交付”。'),
    (H2 '14.1 差异决策矩阵'),
    (T @('能力', 'V3.0 现状', '初版决策', '责任模块') @(
        @('主机器人选择流水线', '仅 @绑定 Agent', '必须：支持“使用【流水线别名】+任务”精确命令', '01、04'),
        @('在同一机器人中指定角色', '机器人唯一绑定单 Agent', '必须：增加 ORCHESTRATOR 入口模式，按 agentCode 精确路由', '01、04'),
        @('飞书用户权限', '记录 senderOpenId 但不映射用户', '必须：绑定 JEECG 用户并按租户、部门、按钮权限授权', '04'),
        @('前序结果传递', '仅 output/summary', '必须：结构化输出和 artifactRefs 自动注入后继 Agent', '02、03、04'),
        @('文档与系统产出', '没有统一产出物模型', '必须：增加 ai_artifact 和共享 workspace 引用', '03、05'),
        @('错误后用户介入', '只有通知、取消和新运行重试', '必须：WAITING、介入记录、补充信息、原运行恢复', '03、04、05'),
        @('检验不通过返工', 'DAG 无回环', '初版支持固定返工分支和同节点人工重试；通用循环后置', '02、03'),
        @('最终交付', '运行中心展示结果', '必须：终态汇总节点输出产出物清单和飞书交付消息', '03、04、05'),
        @('平台内代码执行', '明确排除 Sandbox Broker', '后置；初版通过外部 Agent Connector 返回仓库、构建和部署引用', '04'),
        @('自然语言自由识别流水线', '未定义', '后置；初版使用精确命令和别名，避免误触发', '04')
    )),
    (H2 '14.2 初版必须能力'),
    (P '1. 同时支持 DIRECT_AGENT 和 ORCHESTRATOR 两种飞书机器人入口。推荐一个主机器人承接流水线和角色命令；每个角色独立机器人作为可选直接入口。'),
    (P '2. 命令首期采用确定语法：使用【pipelineCode 或别名】，任务内容；角色【agentCode】，任务内容。禁止用不确定的大模型意图分类直接启动高风险运行。'),
    (P '3. Agent 统一返回 status、summary、output、artifacts、needsUser、userPrompt、retryable。后继节点读取的是已持久化结构化结果和产出物引用。'),
    (P '4. 新增 ai_artifact，记录 runId、nodeRunId、类型、名称、URI、校验值、版本和元数据。初版支持 TEXT、JSON、FEISHU_DOC、GIT_REPO、COMMIT、BUILD、TEST_REPORT、DEPLOYMENT_URL 和 OTHER。'),
    (P '5. ai_run 保存 workspace_context_json，只保存受控的外部工作区、仓库和分支引用。JEECG 初版不执行任意 Shell，不承诺内置沙箱。'),
    (P '6. 技术重试耗尽、Agent 返回 needsUser=true 或缺少业务输入时，节点和运行进入 WAITING。系统创建介入记录并在原飞书线程发送补充信息、重试或终止入口。'),
    (P '7. 用户回复必须解析到唯一未解决介入记录，幂等更新补充输入，将同一节点 WAITING 转回 PENDING 并恢复原 runId。不能用新运行冒充断点恢复。'),
    (P '8. 飞书只发送运行开始、角色阶段完成、等待用户、恢复、失败和最终交付等业务里程碑；Redis、Outbox 和数据库内部转换不发送。'),
    (H2 '14.3 公共状态与数据模型修订'),
    (P '运行状态仍为 CREATED、RUNNING、WAITING、SUCCESS、FAILED、CANCELED。节点状态修订为 PENDING、RUNNING、WAITING、SUCCESS、FAILED、SKIPPED、CANCELED；模块 03 实施前按此版本冻结。'),
    (T @('新增或调整对象', '关键内容', '初版约束') @(
        @('ai_feishu_bot', 'entry_mode、command_enabled', 'DIRECT_AGENT 或 ORCHESTRATOR；启用时校验对应配置'),
        @('ai_pipeline', 'trigger_aliases、notification_bot_id', '别名在同一租户内唯一；发送机器人与默认会话均有效'),
        @('ai_feishu_user_binding', 'sender_open_id、user_id、tenant_id、enabled', '飞书入口必须解析为有效用户后才能启动流程或角色'),
        @('ai_feishu_session', 'chat_id、thread_id、run_id、status', '一个线程只能有一个活动运行或明确指定 runNo'),
        @('ai_run_intervention', 'prompt、actions、status、reply、resume_token', '一个节点同时最多一个 OPEN 介入；回复和按钮操作幂等'),
        @('ai_artifact', 'type、name、uri、checksum、version、metadata_json', '只保存引用和必要小文本；密钥、完整提示词和鉴权头禁止进入'),
        @('ai_run', 'workspace_context_json', '不可由普通用户覆盖 Connector 地址或执行命令')
    )),
    (H2 '14.4 初版端到端流程'),
    (P '飞书用户发送“使用【软件交付】，为我创建库存系统……”；主机器人解析精确别名并完成用户授权；系统创建运行与会话绑定；产品、UI、开发、测试、检验 Agent 依次消费前序结构化结果和产出物；检验不通过时进入固定返工分支或等待用户；最终汇总所有产出物并在原线程交付。下一个 Agent 由执行引擎推进，不由飞书消息或 Agent 自行互调触发。'),
    (H2 '14.5 后续版本能力'),
    (T @('能力', '后置原因') @(
        @('自由自然语言选择流水线和动态组队', '误触发和权限风险高，初版精确命令足够完成闭环'),
        @('通用循环、并行汇聚和动态子任务', '需要 iteration/attempt/join 契约和更复杂恢复语义'),
        @('平台内置 Docker 沙箱、任意代码执行和自动部署', '安全边界和资源隔离必须独立设计'),
        @('多人员审批、会签和升级链', '初版只做单用户补充、重试和终止'),
        @('流式输出、复杂卡片持续更新和多渠道', '不影响首期可恢复协作闭环'),
        @('MinIO、产出物全文存储和版本差异', '初版保存外部 URI 与小型结构化内容即可'),
        @('成本预算、20+ 并发和独立 Executor', '试运行稳定后再扩展')
    )),
    (H2 '14.6 修订里程碑与验收'),
    (P '总周期调整为 10 至 12 周。第 1 周先完成模块 01 二次实施和真实环境最小验收；第 2 至 4 周冻结并实现 PipelineDefinition 1.1、产出物与介入状态契约；第 5 至 8 周完成执行、飞书入口和恢复；第 9 至 10 周完成运行中心与五角色端到端；第 11 至 12 周用于真实外部 Agent、权限、安全和恢复联调。'),
    (P '初版总体验收必须包含：一个主机器人按别名启动五角色流水线；每个角色获得前序产出；至少产生需求文档、设计说明、代码仓库或提交、测试报告和检验结论五类引用；模拟一次 WAITING 并由用户回复恢复同一运行；最终消息包含运行状态、每步摘要、全部产出物和未完成项。')
)

$module01Blocks = @(
    (H1 '10. V1.1 二次实施增补'),
    (P '本章是模块 01 已实施后的前向增补，不回滚现有 Agent、Connector、飞书机器人、加密、测试和长连接实现。数据库调整必须使用新的 Flyway 迁移，禁止修改已执行迁移。若与前文的 HTTP 回调、Verification Token 或机器人唯一绑定描述冲突，以本章为准。'),
    (H2 '10.1 保留能力'),
    (P '保留 /api/ai/agents、connectors、feishu-bots，AES-256-GCM 密钥处理、HTTP Connector、Mock Agent、启停约束、SDK 长连接管理和 RECEIVE_ONLY 事件接收。当前长连接使用 App ID 与 App Secret；Verification Token 和 Encrypt Key 仅属于未来 HTTP 回调模式。'),
    (H2 '10.2 必须二次实施'),
    (T @('改造项', '具体调整', '原因') @(
        @('机器人入口模式', 'ai_feishu_bot 增加 entry_mode=DIRECT_AGENT|ORCHESTRATOR 和 command_enabled', '一个主机器人需要选择流水线或角色，不能被唯一绑定规则锁死'),
        @('唯一绑定规则', '仅 DIRECT_AGENT 机器人继续执行“最多绑定一个启用 Agent”；ORCHESTRATOR 不绑定单 Agent', '兼容已实施逻辑，同时支持主入口'),
        @('授权 Agent 解析', '新增按当前 JEECG 用户数据范围查询启用 Agent 的 options/resolve 服务', '模块 02 发布和模块 04 角色路由不能直接 getById'),
        @('事件移交接口', 'FeishuMessageEventReceiver 校验并提取最小元数据后立即提交内部处理器，不在 SDK 回调线程执行运行创建', '复用已实施长连接并避免阻塞'),
        @('配置页面', '机器人页面展示入口模式、连接状态和“仅接收/可处理”状态；ORCHESTRATOR 不显示 Agent 绑定要求', '避免配置误导'),
        @('审计脱敏', '命令正文、补充输入、系统提示词和产出物内容不进入 @AutoLog 请求正文', '新增入口扩大敏感信息面')
    )),
    (H2 '10.3 前向迁移与接口'),
    (P '新增迁移只补充机器人入口模式、默认值和索引。已有机器人默认迁移为 DIRECT_AGENT，确保当前行为不变。新增 GET /api/ai/agents/options 和面向发布/运行的授权解析服务；不得修改现有无密钥快照的安全边界。'),
    (H2 '10.4 二次实施测试'),
    (P '验证已有 DIRECT_AGENT 绑定不变；ORCHESTRATOR 可在不绑定 Agent 的情况下启用；两个并发 DIRECT_AGENT 仍不能绑定同一机器人；跨租户或部门的 Agent options 不可见；长连接事件处理异常不导致 SDK 接收线程长时间阻塞；页面和接口不回显任何凭据。'),
    (H2 '10.5 二次实施完成标准'),
    (P '模块 01 只有在原三类配置真实环境验收通过、机器人两种入口模式可配置、授权 Agent 解析可供模块 02/04 使用、长连接事件可可靠移交且现有 DIRECT_AGENT 回归通过后，才视为对协作流水线初版就绪。')
)

$module02Blocks = @(
    (H1 '16. V1.2 协作流水线初版修订'),
    (P '本章将待冻结的执行契约升级为 PipelineDefinition 1.1。模块 02 尚未实施，因此不为旧的 1.0 示例保留运行兼容性；实现、夹具和模块 03 联调统一以 1.1 为准。仍保持五类节点和 DAG，不引入通用循环或人工节点。'),
    (H2 '16.1 PipelineDefinition 1.1 增补'),
    (T @('区域', '新增字段或规则') @(
        @('pipeline', 'triggerAliases、notificationBotId、defaultFeishuChatId、interventionPolicy、finalSummaryTemplate'),
        @('AGENT config', 'stageCode、agentId、input、artifactInputs、onError'),
        @('AGENT result contract', 'status、summary、output、artifacts、needsUser、userPrompt、retryable'),
        @('END config', 'output、artifactSelection、completionSummary'),
        @('template', '允许引用 run.input、上游 output、summary 和 artifacts；禁止函数、脚本、SpEL 和任意路径求值')
    )),
    (P 'triggerAliases 在同一租户内唯一，发布时规范化。飞书命令只匹配已启用流程的 code、name 或精确别名；出现多匹配时拒绝启动并返回候选项。'),
    (P 'interventionPolicy 首期只支持 onAgentNeedsUser=WAIT、onRetriesExhausted=WAIT|FAIL、allowedActions=SUPPLY_INPUT|RETRY|CANCEL。正式审批、多人会签和任意跳转后置。'),
    (H2 '16.2 角色协作与产出物规则'),
    (P 'Agent 身份仍由模块 01 的 agentCode、名称和 systemPrompt 定义，流程节点通过 stageCode 表达产品、UI、开发、测试、检验等阶段。后继节点必须通过结构化 input 和 artifactInputs 显式声明所需前序产出，禁止隐式读取其他节点数据库记录。'),
    (P 'artifactInputs 只能引用当前节点的支配祖先。END 的 artifactSelection 汇总可交付引用；若某分支产出不保证存在，必须提供默认值、可选标记或调整图结构。'),
    (H2 '16.3 返工边界'),
    (P '初版流程仍是 DAG。测试或检验不通过可连接到预先绘制的 DevFix、Retest 等后续节点，或由 WAITING 介入选择重试当前节点。禁止边回到已执行祖先。通用有限循环、动态返工次数和子流程在 schemaVersion 2.x 设计。'),
    (H2 '16.4 校验与页面增补'),
    (P '发布校验增加别名冲突、stageCode 唯一性、artifactInputs 支配关系、Agent 结果契约版本、介入策略枚举、最终产出物选择和通知机器人入口权限。流程编辑页增加阶段名称、前序产出选择、错误处理策略和最终交付配置；版本详情对 systemPrompt、Connector 地址和内部工作区信息继续脱敏。'),
    (H2 '16.5 模块 01 二次实施依赖'),
    (P 'P0 必须增加：ORCHESTRATOR 机器人可配置；Agent options 和发布解析服务应用租户/部门数据范围；真实飞书长连接状态可见。02 不直接依赖 FeishuMessageEventReceiver，但必须发布 notificationBotId 和 triggerAliases 供模块 04 使用。'),
    (H2 '16.6 验收增补'),
    (P '发布一条产品→UI→开发→测试→检验的五角色流程，验证每个节点显式接收前序 output/artifactRefs；验证固定返工分支合法而回边被拒绝；验证同租户别名冲突、越权 Agent、非法 artifact 引用和未配置介入策略均被拒绝；模块 03 能只依赖 1.1 公共 DTO 解析。')
)

$module03Blocks = @(
    (H1 '12. V1.1 人工介入与产出物修订'),
    (P '本章扩展模块 03，使执行引擎能够支撑多角色产出传递和飞书人工介入。Redis 5、MySQL 权威状态、Outbox、专用线程池和五节点 Handler 的原设计保持不变。'),
    (H2 '12.1 状态机修订'),
    (P '节点状态改为 PENDING、RUNNING、WAITING、SUCCESS、FAILED、SKIPPED、CANCELED。允许 RUNNING→WAITING、WAITING→PENDING、WAITING→FAILED、WAITING→CANCELED。运行存在 WAITING 节点且无活动执行时进入 WAITING；恢复节点入队后回到 RUNNING。每个状态转换仍通过条件更新和事件记录保证幂等。'),
    (H2 '12.2 新增数据对象'),
    (T @('对象', '用途', '关键约束') @(
        @('ai_run_intervention', '保存等待用户的问题、允许动作、回复和恢复状态', 'node_run_id 同时最多一个 OPEN；resume_token 和 source_message_id 唯一'),
        @('ai_artifact', '保存角色产出的外部文档、仓库、提交、构建、测试和部署引用', 'run_id/node_run_id 索引；URI、摘要和元数据脱敏'),
        @('ai_run.workspace_context_json', '保存各角色共享的受控工作区引用', '创建后仅由可信 Connector 结果合并，用户输入不能覆盖执行地址'),
        @('ai_node_run.output_json', '保存统一结果和 artifactIds', '不得保存明文密钥、完整鉴权头或未截断原始响应')
    )),
    (H2 '12.3 Agent 结果与推进'),
    (P 'Connector 结果 status=SUCCESS 时，在同一事务写节点输出、ai_artifact、事件和后继 NODE_READY Outbox；status=NEEDS_INPUT 或 needsUser=true 时写介入记录并转 WAITING；FAILED 且可重试时按技术重试策略处理，耗尽后根据节点 onError 进入 WAITING 或 FAILED。'),
    (P '后继 Agent 输入由 PipelineDefinition 1.1 的 input 和 artifactInputs 在执行前解析，只传递允许字段和产出物引用。下一 Agent 只能由执行器在前一节点事务成功后触发，禁止 Agent 自行调用其他 Agent 改变运行状态。'),
    (H2 '12.4 恢复接口'),
    (T @('接口', '行为') @(
        @('GET /api/ai/runs/{id}/interventions/open', '查询当前用户有权处理的未解决介入'),
        @('POST /api/ai/runs/{id}/interventions/{interventionId}/resolve', '提交 SUPPLY_INPUT、RETRY 或 CANCEL；requestId 幂等'),
        @('GET /api/ai/runs/{id}/artifacts', '查询脱敏产出物列表'),
        @('GET /api/ai/runs/{id}/summary', '返回阶段完成情况、交付清单和未完成项')
    )),
    (P 'SUPPLY_INPUT 将补充内容作为独立 resumeInput 保存，不覆盖原始 run input；成功解决介入后以同一 nodeRunId 重新入队。重复飞书回复或重复按钮请求返回既有结果，不重复执行。'),
    (H2 '12.5 取消、重试和返工'),
    (P '取消时将 PENDING 和 WAITING 节点置 CANCELED，并关闭 OPEN 介入。人工“重试当前节点”用于原运行断点恢复；运行中心原有“从失败运行重试”仍创建新 runId。固定返工分支按普通 DAG 节点推进，不引入运行时回边。'),
    (H2 '12.6 测试与验收增补'),
    (P '覆盖 NEEDS_INPUT→WAITING→用户补充→同 runId 恢复；重复回复只恢复一次；介入期间应用重启后仍可处理；产出物写入与后继入队保持事务一致；Connector 返回恶意 URI、密钥字段或超大 metadata 被拒绝或脱敏；五角色流程的最终 summary 能列出每步产出和未完成项。')
)

$module04Blocks = @(
    (H1 '12. V1.1 主入口、角色路由与协作恢复修订'),
    (P '本章以模块 01 已实施的飞书官方 Java SDK 长连接为基线，替代前文默认 HTTP callback、URL verification、Verification Token 和 Encrypt Key 的首期方案。HTTP 回调可作为后续兼容入口，但不在初版重复建设。'),
    (H2 '12.1 机器人入口模式'),
    (T @('模式', '行为', '适用场景') @(
        @('DIRECT_AGENT', '保持一个启用机器人最多绑定一个启用 Agent，@机器人文本创建 AGENT_DIRECT', '每个角色一个机器人'),
        @('ORCHESTRATOR', '不绑定单一 Agent；解析流水线或角色命令并按用户权限创建运行', '推荐的统一主机器人')
    )),
    (P '推荐部署一个 ORCHESTRATOR 主机器人作为统一入口，同时允许产品、UI、开发、测试、检验等 Agent 使用独立 DIRECT_AGENT 机器人。多机器人之间不互相发消息触发节点，所有协作由同一执行引擎和 runId 管理。'),
    (H2 '12.2 初版命令协议'),
    (P '流水线命令：使用【pipelineCode、名称或精确别名】，任务描述。角色命令：角色【agentCode】，任务描述。运行等待时，用户在原线程回复补充内容，或使用卡片中的补充信息、重试、终止操作。无法唯一匹配、用户无权限或线程存在多个活动运行时拒绝执行并返回可选项。'),
    (H2 '12.3 身份、权限和会话'),
    (P '新增 ai_feishu_user_binding，将 senderOpenId 绑定到 JEECG 用户和租户。ORCHESTRATOR 启动流程或角色前必须应用该用户的租户、部门和按钮权限；未绑定用户只能收到绑定指引，不能创建运行。'),
    (P '新增 ai_feishu_session，将 chatId、threadId、runId 和活动状态关联。原线程回复优先解析到唯一 OPEN intervention；终态后普通消息不会隐式复活旧运行。source event/message/request ID 全链路去重。'),
    (H2 '12.4 长连接事件处理'),
    (P 'FeishuMessageEventReceiver 只完成 App ID 对应机器人定位、消息最小字段提取、事件去重键生成和快速移交。命令解析、权限查询、运行创建、介入恢复和消息发送均在独立处理器执行，不能阻塞 SDK WebSocket 回调线程。当前 RECEIVE_ONLY 状态在接入处理器后调整为 COMMAND_ENABLED。'),
    (H2 '12.5 Connector 结果修订'),
    (P '统一结果增加 status=SUCCESS|NEEDS_INPUT|FAILED、artifacts、needsUser、userPrompt。artifacts 只接受平台允许类型和 URI 协议；外部 Agent 可以返回飞书文档、Git 仓库、提交、构建、测试报告和部署地址。初版不在 JEECG 内执行任意代码。'),
    (H2 '12.6 通知策略'),
    (T @('事件', '消息内容') @(
        @('RUN_STARTED', '运行号、流水线、目标、角色阶段'),
        @('STAGE_COMPLETED', '角色、摘要、新增产出物、下一阶段'),
        @('USER_INPUT_REQUIRED', '阻塞摘要、需要的信息、允许操作、详情链接'),
        @('RUN_RESUMED', '补充信息已接收、恢复节点'),
        @('RUN_FAILED/CANCELED', '已完成阶段、失败点、已有产出物和后续选择'),
        @('RUN_SUCCEEDED', '每阶段摘要、完整交付清单、仓库/文档/测试/部署链接、遗留项')
    )),
    (P '技术重试开始、Redis pending、Outbox 重投和数据库内部状态不发飞书。只有技术重试耗尽或明确需要业务输入时寻找用户介入，避免消息风暴。'),
    (H2 '12.7 初版验收增补'),
    (P '使用一个 ORCHESTRATOR 主机器人按别名启动五角色流程；使用角色命令直接运行指定 Agent；使用一个 DIRECT_AGENT 机器人验证兼容链路；模拟 NEEDS_INPUT 后在原线程补充并恢复同一运行；重复事件、回复和卡片点击均不重复创建运行或执行节点；最终消息包含全部产出物链接且无密钥泄漏。')
)

$module05Blocks = @(
    (H1 '12. V1.1 协作流水线闭环验收修订'),
    (P '本章把运行中心和最终验收扩展到角色阶段、产出物、用户介入和飞书主入口。原有 Redis 5 恢复、取消、新运行重试、部署和五并发要求继续有效。'),
    (H2 '12.1 运行中心增补'),
    (T @('区域', '新增内容') @(
        @('运行列表', '流水线别名、当前角色阶段、WAITING 原因、产出物数量'),
        @('详情', '角色阶段输入输出、共享 workspace 摘要、脱敏产出物'),
        @('介入', '问题、允许动作、发起时间、处理人、回复摘要和恢复结果'),
        @('产出物', '类型、名称、版本、来源节点、校验值和外部链接'),
        @('时间线', 'RUN_STARTED、STAGE_COMPLETED、USER_INPUT_REQUIRED、RUN_RESUMED 和最终交付'),
        @('操作', '处理介入、取消运行、从失败运行新建重试；权限分别控制')
    )),
    (H2 '12.2 部署配置增补'),
    (P '新增主机器人入口开关、精确命令前缀、飞书用户绑定策略、活动会话超时、介入超时、允许产出物 URI 协议、单节点产出数量、metadata 大小和产出物保留期配置。敏感值继续通过环境变量或 Secret 注入。'),
    (H2 '12.3 测试分层增补'),
    (P '单元测试增加命令解析、别名冲突、Agent 结果契约、产出物白名单和介入状态机；集成测试增加介入事务、同 runId 恢复、会话关联和产出物事务；接口与安全测试增加飞书用户映射、跨租户流水线/Agent IDOR、重复回复和恶意产出物 URI；前端测试增加 WAITING 操作和产出物展示。'),
    (H2 '12.4 必须执行的五角色 E2E'),
    (P '1. 配置产品、UI、开发、测试、检验五个 Agent，允许复用 Connector，但 systemPrompt 和 stageCode 不同。'),
    (P '2. 发布“软件交付”流水线，配置精确别名、主机器人、默认会话、介入策略和最终交付模板。'),
    (P '3. 飞书发送“使用【软件交付】，为我创建库存系统，要求……”并确认只创建一个运行。'),
    (P '4. 验证产品需求、UI 说明、代码仓库或提交、测试报告、检验结论依次写入 ai_artifact，后继角色收到允许的前序引用。'),
    (P '5. 让测试 Agent 返回 NEEDS_INPUT；确认运行进入 WAITING，飞书原线程收到介入消息；用户补充后恢复同一 runId。'),
    (P '6. 检验 Agent 完成后，飞书收到每阶段摘要、交付清单、未完成项和运行中心地址。'),
    (P '7. 重复发送原事件、重复回复和重复点击卡片，确认不重复创建运行、不重复恢复、不重复执行成功节点。'),
    (P '8. 在 WAITING 和 RUNNING 两种状态重启 JEECG，确认介入记录、Outbox 和 Redis pending 均可恢复。'),
    (H2 '12.5 验收门槛'),
    (P '初版只有在模块 01 二次实施完成、PipelineDefinition 1.1 契约测试通过、五角色 E2E 通过、真实飞书主机器人和至少一个真实外部 Agent Connector 联调成功、产出物和介入权限无跨租户泄漏、5 条并发仍稳定后方可封板。'),
    (H2 '12.6 后续版本边界'),
    (P '自由自然语言路由、通用循环、并行 Agent、动态组队、平台内置沙箱、自动部署、多人员审批、MinIO、流式卡片和 20+ 并发不作为初版失败项，但必须在路线图中单独立项，不得在当前接口中预留可执行脚本或绕过权限的万能字段。')
)

$jobs = @(
    @{
        Source = 'C:\Users\Administrator\Desktop\设计计划.docx'
        Destination = Join-Path $outputDirectory '00-总体实施计划-V3.1-飞书协作流水线初版修订.docx'
        Notice = '修订提示：本文保留 V3.0 正文作为历史基线，新增第 14 章 V3.1 强制修订；冲突内容以第 14 章为准。'
        Blocks = $overallBlocks
    },
    @{
        Source = 'C:\Users\Administrator\Desktop\多Agent项目模块计划\01-基础配置与Agent管理计划.docx'
        Destination = Join-Path $outputDirectory '01-基础配置与Agent管理计划-V1.1-二次实施增补.docx'
        Notice = '修订提示：模块 01 已实施，本文新增第 10 章二次实施增补；使用前向迁移保留既有行为。'
        Blocks = $module01Blocks
    },
    @{
        Source = 'D:\Project\myAgentProduct\docs\02-流程设计器与流程定义计划-优化版.docx'
        Destination = Join-Path $outputDirectory '02-流程设计器与流程定义计划-V1.2-协作流水线修订.docx'
        Notice = '修订提示：本文新增第 16 章并将待冻结执行契约统一为 PipelineDefinition 1.1；冲突内容以第 16 章为准。'
        Blocks = $module02Blocks
    },
    @{
        Source = 'C:\Users\Administrator\Desktop\多Agent项目模块计划\03-执行引擎与Redis调度计划.docx'
        Destination = Join-Path $outputDirectory '03-执行引擎与Redis调度计划-V1.1-人工介入与产出物修订.docx'
        Notice = '修订提示：本文新增第 12 章人工介入与产出物修订；状态机和数据对象以第 12 章为准。'
        Blocks = $module03Blocks
    },
    @{
        Source = 'C:\Users\Administrator\Desktop\多Agent项目模块计划\04-Agent连接器与飞书协作计划.docx'
        Destination = Join-Path $outputDirectory '04-Agent连接器与飞书协作计划-V1.1-入口路由修订.docx'
        Notice = '修订提示：本文新增第 12 章，初版飞书接入以已实施的 SDK 长连接和主入口路由为准。'
        Blocks = $module04Blocks
    },
    @{
        Source = 'C:\Users\Administrator\Desktop\多Agent项目模块计划\05-运行中心部署测试计划.docx'
        Destination = Join-Path $outputDirectory '05-运行中心部署测试计划-V1.1-闭环验收修订.docx'
        Notice = '修订提示：本文新增第 12 章，将产出物、用户介入和五角色飞书流水线纳入初版封板门槛。'
        Blocks = $module05Blocks
    }
)

foreach ($job in $jobs) {
    Add-RevisionAppendix -SourcePath $job.Source -DestinationPath $job.Destination -RevisionNotice $job.Notice -Blocks $job.Blocks
}

Get-ChildItem -LiteralPath $outputDirectory -Filter '*.docx' | Select-Object Name, Length, LastWriteTime
