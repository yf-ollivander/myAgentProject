# 模型 Connector 配置与使用指南

更新时间：2026-08-11

适用范围：OpenAI-compatible、DeepSeek、Anthropic、Gemini、Ollama 和 Custom Connector。当前版本仅支持同步、非流式文本调用，不支持 SSE、Tool Calling、多模态、视觉或音频输入。

## 1. 先理解完整调用链

实际使用顺序是：

1. 数据库具备 `_1` 至 `_7` 的表和字段。
2. 后端配置稳定的加密密钥、非空 Host allowlist，并启用 HTTP Gateway。
3. 在“模型 Connector”页面新增 Connector，保存、测试、启用。
4. 在“Agent 管理”页面新增 Agent，选择已启用的 Connector，测试、启用。
5. 单 Agent 通过运行 API 以 `AGENT_DIRECT` 启动；Pipeline 需要先设计、校验、发布、启用，再通过运行 API 启动。
6. 通过运行详情、事件、摘要和产出物 API 查看结果。

来源：`AiConnectorController`、`AiAgentController`、`AiPipelineController`、`AiRunController` 以及 `RunCreationService`。

## 2. 数据库准备

### 2.1 已有数据库

如果 `_1` 至 `_6` 已经执行，只执行：

`jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.3_7__multi_agent_connector_provider.sql`

可在 MySQL 客户端中执行：

```sql
USE jeecgboot;
SOURCE D:/Project/myAgentProduct/jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.3_7__multi_agent_connector_provider.sql;
```

执行后核对：

```sql
SHOW COLUMNS FROM ai_connector WHERE Field IN
  ('provider_type', 'model_name', 'model_options', 'model_response_mode');

SELECT id, name, url
FROM sys_permission
WHERE id = '2026071300000000003';
```

预期：四个字段存在，菜单名称为“模型 Connector”，路由仍是 `/multi-agent/connectors`。

### 2.2 全新 Docker 数据库

全新的 MySQL 数据卷会通过 `jeecg-boot/db/Dockerfile` 导入 `multi-agent-config.sql`。已有数据卷不会重新执行初始化脚本，不能用重建容器代替 `_7` 迁移。

来源：`V3.9.3_7__multi_agent_connector_provider.sql`、`jeecg-boot/db/multi-agent-config.sql`、`jeecg-boot/db/Dockerfile`。开发和生产配置还排除了 Flyway 自动配置，因此生产部署不要假定应用启动会自动补迁移。

## 3. 后端必须配置的环境变量

### 3.1 生成凭据加密密钥

只生成一次，并保存到 Secret 管理系统：

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

生成值设置为 `AI_CONFIG_SECRET_KEY`。它必须是 Base64 解码后 32 字节，或直接是 32 个 ASCII 字符。所有后端实例必须使用同一个值；更换或丢失后，数据库中的旧凭据将无法解密。

来源：`application.yml` 的 `ai.agent.secret-key` 和 `SecretCipherService`。

### 3.2 配置 Host allowlist

只填写主机名，不填写协议、端口和路径。按实际使用的 Provider 选择：

```text
api.openai.com,
api.deepseek.com,
api.anthropic.com,
generativelanguage.googleapis.com
```

合并为一个环境变量时使用英文逗号：

```powershell
$env:AI_AGENT_ALLOWED_HOSTS = 'api.openai.com,api.deepseek.com,api.anthropic.com,generativelanguage.googleapis.com'
```

私有网关使用精确域名，例如 `llm.internal.example.com`。`*.example.com` 只匹配子域名，不匹配裸域名。不要为了省事配置过宽的通配符。

权威行为：allowlist 为空时，Connector 测试和正式运行都会返回 `CONNECTOR_HOST_NOT_ALLOWED`。旧操作手册中“空值允许所有主机”的描述不适用于当前统一 Transport。

来源：`AiAgentProperties.readAllowedHosts`、`ConnectorHttpTransport.endpoint`、`ConnectorUriPolicy` 和 `README-AI.md`。

### 3.3 启用正式 HTTP Gateway

必须设置：

```powershell
$env:AI_EXECUTOR_GATEWAY = 'http'
```

配置文件当前默认是 `http`；环境变量可覆盖该默认值。如果显式设置为 `disabled`，Connector 单独测试可能成功，但启动单 Agent 或 Pipeline 会返回 `EXECUTOR_GATEWAY_UNAVAILABLE`。

来源：`application.yml`、`application-dev.yml` 和 `application-prod.yml` 使用 `${AI_EXECUTOR_GATEWAY:http}`；`HttpAgentExecutionGateway` 仅在值为 `http` 时创建；`RunCreationService.validateCommon` 会拒绝 disabled Gateway。

### 3.4 本地 PowerShell 完整示例

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\ms-17.0.16'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:AI_CONFIG_SECRET_KEY = '<上一步生成的值>'
$env:AI_AGENT_ALLOWED_HOSTS = 'api.deepseek.com'
$env:AI_EXECUTOR_GATEWAY = 'http'
$env:AI_MOCK_AGENT_ENABLED = 'false'
```

修改环境变量后必须重启后端。

### 3.5 Docker Compose 特别说明

项目根目录 `docker-compose.yml` 已传递 `AI_CONFIG_SECRET_KEY`、`AI_AGENT_ALLOWED_HOSTS` 和 `AI_EXECUTOR_GATEWAY`。未设置宿主机环境变量或 `.env` 时，Compose 使用与应用配置一致的官方域名 allowlist 和 `http` Gateway 默认值。

本机 `.env` 示例：

```dotenv
AI_CONFIG_SECRET_KEY=<生成的稳定密钥>
AI_AGENT_ALLOWED_HOSTS=api.deepseek.com
AI_EXECUTOR_GATEWAY=http
AI_MOCK_AGENT_ENABLED=false
```

来源：项目根目录 `docker-compose.yml` 的 `jeecg-boot-system.environment`、`application.yml` 和 Spring Boot 环境变量宽松绑定规则。

## 4. 启动与权限

后端启动类：`org.jeecg.JeecgSystemApplication`。默认地址为 `http://localhost:8080/jeecg-boot`。

前端：

```powershell
cd D:\Project\myAgentProduct\jeecgboot-vue3
pnpm dev
```

默认地址为 `http://localhost:3100`。

非管理员角色至少需要按职责授予：

- Connector：`ai:connector:list/add/edit/test/enable/disable/delete`
- Agent：`ai:agent:list/add/edit/test/enable/disable/delete`
- Pipeline：`ai:pipeline:list/add/edit/validate/publish/enable/disable`
- Run：`ai:run:list/start/cancel/retry/intervene`

授权后重新登录，使动态菜单和按钮权限重新加载。

来源：四个 Controller 上的 `@RequiresPermissions`，以及 `_1`、`_4`、`_5`、`_7` 迁移中的菜单权限。

## 5. 选择 Provider

| Provider | 页面自动填充 | 凭据 | Model Name 示例 |
| --- | --- | --- | --- |
| OpenAI-compatible | `https://api.openai.com` + `/v1/chat/completions` | Bearer Token | 填账号实际可用的模型 ID，例如 `gpt-4.1-mini` |
| DeepSeek | `https://api.deepseek.com` + `/chat/completions` | Bearer Token | 常见示例 `deepseek-chat` |
| Anthropic | `https://api.anthropic.com` + `/v1/messages` | API Key，Header 为 `x-api-key` | 填 Anthropic 控制台实际模型 ID |
| Gemini | `https://generativelanguage.googleapis.com` + `/v1beta/models/{model}:generateContent` | API Key，Header 为 `x-goog-api-key` | 填 Google 控制台实际模型 ID |
| Ollama | `http://127.0.0.1:11434` + `/api/chat` | 无鉴权 | 使用 `ollama list` 显示的名称，例如 `qwen3:8b` |
| Custom | 自行填写 | NONE、Bearer 或 API Key | Custom 不使用 Model Name |

Model Name 示例不是项目硬编码值，最终以供应商账号的模型列表或 `ollama list` 为准。代码只校验模型名非空，不替你选择模型。

Docker 中调用宿主机 Ollama 时，`127.0.0.1` 指向后端容器自身。通常应把 Base URL 改为 `http://host.docker.internal:11434`，并把 `host.docker.internal` 加入 allowlist，同时确保 Ollama 对容器网络可达。

来源：前端 `connector.contract.ts` 和后端 `ConnectorProviderType` 的 Provider 预设。

## 6. 新增模型 Connector

进入“多 Agent 管理 → 模型 Connector”，点击“新增”。

### 6.1 基础字段

1. `Connector 代码`：稳定标识，以字母开头，只使用字母、数字、下划线和连字符，例如 `deepseek_primary`。保存后不可修改。
2. `名称`：给操作人员看的名称，例如“DeepSeek 主账号”。
3. `Provider`：选择实际供应商。
4. `Model Name`：填写供应商实际模型 ID；五类模型 Provider 必填。
5. `Base URL`、`Path`：优先保留页面预设。私有 OpenAI-compatible 网关只修改确有差异的部分。
6. `鉴权类型`：按预设使用 Bearer、API Key 或 NONE。
7. `凭据`：填写 Token/API Key。后端加密保存，编辑时不会回显；留空表示保留旧凭据，只有勾选“清除已配置凭据”才显式清除。

Provider 切换只替换空值或仍等于旧预设的字段；页面出现“已保留自定义值”时，要人工确认保留值仍适用于新 Provider，并重新测试。

来源：`AiConnectorDrawer.vue`、`switchConnectorProvider` 和 `AiConnectorServiceImpl.apply`。

### 6.2 选择结果模式

初次接入推荐选“纯文本（TEXT）”。模型只需返回普通文本，系统转换为：

```json
{
  "contractVersion": "1.1",
  "status": "SUCCESS",
  "summary": "最多 1000 字符的摘要",
  "output": { "text": "模型完整文本" },
  "artifacts": [],
  "needsUser": false,
  "retryable": false
}
```

只有需要模型主动返回 `NEEDS_INPUT`、Artifacts、失败码或 retryable 语义时，才选择 `RESULT_1_1`。该模式要求模型返回没有 Markdown 代码围栏的严格 JSON，例如：

```json
{
  "contractVersion": "1.1",
  "status": "SUCCESS",
  "summary": "处理完成",
  "output": { "answer": "结果" },
  "artifacts": [],
  "needsUser": false,
  "retryable": false
}
```

`RESULT_1_1` 不接受多余未知字段，也不接受 Markdown 的 JSON 代码围栏。OpenAI/DeepSeek 会发送 `response_format=json_object`，Gemini 会发送 `responseMimeType=application/json`，Ollama 会发送 `format=json`，Anthropic 使用系统提示约束。

来源：`ConnectorInvocationService.textResult/strictResult`、`AgentResultValidator` 和四类 Adapter。

### 6.3 生成参数怎么填

第一次使用建议全部留空，让供应商使用默认值。需要调优时：

| 参数 | 通俗含义 | 建议 |
| --- | --- | --- |
| Temperature | 越低越稳定，越高越发散 | 提取、分类、流程任务用 `0` 至 `0.3`；创作用 `0.7` 左右 |
| Top P | 另一种随机度控制 | 一般留空；不要和 Temperature 同时大幅调整 |
| Max Tokens | 最长输出量 | 简短回答可用 `1024`，长报告可用 `4096`，按成本和模型上限调整 |

合法范围分别是 Temperature 0-2、Top P 0-1、Max Tokens 1-65536。未填写就不发送；Anthropic 因接口必填，在未填写时自动使用 4096。

来源：`AiConfigDtos.ModelOptions`、`AiConnectorDrawer.vue` 和各 Provider Adapter。

### 6.4 高级配置

- 非敏感请求头必须是 JSON 对象，例如 `{"X-Tenant":"demo"}`。
- 不要配置 `Authorization`、`Content-Type`、`Host`、Cookie、鉴权 Header 或包含 secret/token/password/key 的 Header；前后端和 Transport 会阻止覆盖。
- 连接超时默认 10 秒，读取超时默认 300 秒，范围都是 1-300 秒。
- 请求和响应最大都是 1 MiB，不跟随重定向。

来源：`AiConnectorDrawer.vue`、`AiConnectorServiceImpl.validateHeaders`、`ConnectorHttpTransport` 和 `AiExecutorProperties`。

## 7. 测试并启用 Connector

1. 保存后，记录仍是“禁用”状态。
2. 点击“测试”。
3. 使用：

```json
{
  "prompt": "请用一句中文回答：连接测试成功"
}
```

4. 成功时列表“最近测试”更新，凭据不会显示在结果中。
5. 再点击“启用”。建议坚持“测试成功后才启用”的操作规范。

输入规则：`input.prompt` 是字符串时，它成为模型主消息；否则系统会稳定序列化整个 `input` JSON 作为主消息。Resume Input 和 Artifact Inputs 会作为独立标记 JSON 段追加，系统不会下载 Artifact URI 内容。

Connector 测试只更新最近测试状态，不创建正式 Run。测试成功也不代表正式 Gateway 已开启，因此仍要确认 `AI_EXECUTOR_GATEWAY=http`。

来源：`AiConnectorList.vue`、`AiConnectorController.test`、`ConnectorInvocationService.renderPrompt` 和 Connector 服务的测试结果持久化逻辑。

## 8. 创建并启用 Agent

进入“Agent 管理”，点击“新增”：

1. `Agent 代码`：例如 `general_writer`，保存后不可修改。
2. `名称`：例如“通用写作 Agent”。
3. `说明`：描述用途。
4. `系统提示词`：写清角色、目标、限制和输出要求。例如：“你是企业内部写作助手。回答使用简体中文，先给结论，不编造未知事实。”
5. `HTTP Connector`：选择刚才创建的 Connector。
6. `任务超时`：1-300 秒；应不小于 Connector 读取超时的实际需要。
7. `最大重试次数`：0-2。只有 408、429、5xx、超时和连接类可重试错误会进入重试语义。
8. 保存，点击“测试”，推荐输入 `{"prompt":"请回复 Agent 测试成功"}`。
9. 测试成功后点击“启用”。Agent 启用时会再次检查 Connector 已启用。

来源：`AiAgentDrawer.vue`、`AiAgentList.vue` 和 `AiAgentServiceImpl.validateForEnable`。

## 9. 创建最简单的 Pipeline

1. 进入“多 Agent 流程”，点击“新增流程”。
2. 进入设计器，放置并连接 `START → AGENT → END`。
3. START 的输入字段新增：名称 `prompt`、类型 `string`、必填。
4. AGENT 节点选择已启用 Agent，输入映射配置：`prompt = {{run.input.prompt}}`。
5. 如果 Connector 使用 TEXT，AGENT 输出字段可声明：`text`、类型 `string`、必填。
6. END 最终输出配置：`answer = {{nodes.<Agent节点ID>.output.text}}`；完成摘要可使用 `{{nodes.<Agent节点ID>.summary}}`。
7. 点击保存、校验、发布。
8. 返回流程列表，点击启用。

模型 Provider 的 TEXT 和 RESULT_1_1 都可发布；Custom 只有 Result 1.1 可发布，Custom LEGACY 会被 `PIPELINE_AGENT_NOT_AVAILABLE` 门禁拒绝。

来源：`AiPipelineDesigner.vue`、`NodePropertyPanel.vue`、`PipelineTemplateResolverTest`、`ConnectorContractPolicy` 和 `PipelinePublishServiceImpl`。

## 10. 启动正式运行

当前仓库没有 `AiRunList` 前端实现，运行能力由 `/api/ai/runs` 后端 API 提供。可以使用 Knife4j、Postman 或 PowerShell。请求需携带登录后取得的 `X-Access-Token`。

### 10.1 直接运行 Agent

```powershell
$headers = @{ 'X-Access-Token' = '<JEECG 登录令牌>' }
$body = @{
  requestId = 'manual-agent-20260811-001'
  runType = 'AGENT_DIRECT'
  agentId = '<Agent ID>'
  input = @{ prompt = '请总结本周工作重点' }
} | ConvertTo-Json -Depth 10

$result = Invoke-RestMethod `
  -Uri 'http://localhost:8080/jeecg-boot/api/ai/runs' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $body

$runId = $result.result.runId
```

### 10.2 运行已发布 Pipeline

```powershell
$body = @{
  requestId = 'manual-pipeline-20260811-001'
  runType = 'PIPELINE'
  pipelineId = '<Pipeline ID>'
  input = @{ prompt = '请总结本周工作重点' }
} | ConvertTo-Json -Depth 10

$result = Invoke-RestMethod `
  -Uri 'http://localhost:8080/jeecg-boot/api/ai/runs' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $body

$runId = $result.result.runId
```

`requestId` 最长 64 字符，是调用方幂等键。同一租户、来源、requestId 和相同请求再次提交会返回 `reused=true`；相同 requestId 配不同请求会冲突。不要每次重试都随意换 requestId。

来源：`ExecutionDtos.RunCreateRequest`、`AiRunController.start` 和 `RunCreationService` 的 request hash/复用逻辑。

## 11. 查询结果和处理异常状态

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/jeecg-boot/api/ai/runs/$runId" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/jeecg-boot/api/ai/runs/$runId/events" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/jeecg-boot/api/ai/runs/$runId/summary" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/jeecg-boot/api/ai/runs/$runId/artifacts" -Headers $headers
```

常见状态：`CREATED → RUNNING → SUCCESS/FAILED`。RESULT_1_1 返回 `NEEDS_INPUT` 时进入 `WAITING`，先查询：

```text
GET /api/ai/runs/{runId}/interventions/open
```

再调用：

```text
POST /api/ai/runs/{runId}/interventions/{interventionId}/resolve
```

请求示例：

```json
{
  "requestId": "resolve-20260811-001",
  "action": "SUPPLY_INPUT",
  "resumeInput": { "prompt": "补充说明内容" }
}
```

还可以调用 `POST /{runId}/cancel` 或 `POST /{runId}/retry`。这些接口分别需要 `ai:run:intervene`、`ai:run:cancel`、`ai:run:retry` 权限。

来源：`AiRunController` 和 `ExecutionDtos`。

## 12. 常见错误排查

| 错误/现象 | 原因 | 处理 |
| --- | --- | --- |
| `CONNECTOR_HOST_NOT_ALLOWED` | allowlist 为空或目标域名未加入 | 填精确主机并重启后端 |
| `CONNECTOR_HOST_UNSAFE` | 域名解析到内网、回环或链路本地地址，且没有精确允许 | 核对部署网络，只在确实可信时加入精确主机 |
| `CONNECTOR_URL_INVALID` | URL、Path、Gemini `{model}` 槽位非法 | 恢复 Provider 预设并逐项修改 |
| `HTTP_401/403` | Key 错误、Header 类型错误或账号无模型权限 | 核对鉴权类型、Header 和供应商控制台权限 |
| `HTTP_429` | 限流或余额/配额不足 | 降低调用频率，检查额度；该错误可重试 |
| `TIMEOUT` | 模型响应超过读取超时 | 适当提高读取超时，减少 Max Tokens |
| `MODEL_RESPONSE_EMPTY` | Provider 返回空文本 | 检查模型、响应结构和网关兼容性 |
| `MODEL_RESPONSE_INVALID` | RESULT_1_1 不是严格 JSON，或厂商响应结构不匹配 | 先切到 TEXT 验证连接，再修正模型输出约束 |
| `AGENT_RESULT_INVALID` | JSON 字段、状态联动或 Artifact 不符合 1.1 | 对照 `AgentResultValidator` 修正 |
| `EXECUTOR_GATEWAY_UNAVAILABLE` | `ai.executor.gateway` 仍是 disabled | 设置 `AI_EXECUTOR_GATEWAY=http` 并重启 |
| Connector 测试成功但 Pipeline 发布失败 | Custom 仍是 LEGACY，或 Agent/Connector 未启用 | Custom 改 Result 1.1，重新测试并启用依赖 |
| Docker Ollama 连接失败 | 容器内 `127.0.0.1` 不是宿主机 | 使用可达的宿主机地址并加入精确 allowlist |

## 13. 最推荐的首次实践

为了减少变量，建议按以下顺序：

1. 先选择一个已有 Key 的公共 Provider，结果模式用 TEXT。
2. 所有采样参数留空，仅填写模型名和凭据。
3. Connector 使用 `{"prompt":"请回复连接成功"}` 测试并启用。
4. 建一个简单 Agent，系统提示词只写角色和语言要求，测试并启用。
5. 用 `AGENT_DIRECT` API 跑通一次，确认 Run 为 SUCCESS。
6. 再创建 `START → AGENT → END` Pipeline，完成保存、校验、发布、启用和运行。
7. 只有业务确实需要 WAITING、Artifacts 或模型声明失败时，再切换 RESULT_1_1。

这样可以把网络、凭据、模型名、结果协议和 Pipeline 编排问题分层排查，不会一次同时引入所有变量。

## 14. 来源索引

- 环境与预设：`README-AI.md`、`application.yml`、`application-prod.yml`、根目录 `docker-compose.yml`
- 数据库：`V3.9.3_7__multi_agent_connector_provider.sql`、`jeecg-boot/db/multi-agent-config.sql`
- Provider 预设：`ConnectorProviderType.java`、`connector.contract.ts`
- Connector 页面：`AiConnectorList.vue`、`AiConnectorDrawer.vue`、`connector.api.ts`
- 参数与校验：`AiConfigDtos.ModelOptions`、`AiConnectorServiceImpl`
- 统一调用与安全：`ConnectorInvocationService`、`ConnectorHttpTransport`、`ConnectorUriPolicy`
- Provider 映射：`OpenAiChatAdapter`、`AnthropicAdapter`、`GeminiAdapter`、`OllamaAdapter`
- Agent：`AiAgentController`、`AiAgentServiceImpl`、`AiAgentDrawer.vue`
- Pipeline：`AiPipelineController`、`PipelinePublishServiceImpl`、`AiPipelineDesigner.vue`
- 正式运行：`AiRunController`、`RunCreationService`、`ExecutionDtos`
