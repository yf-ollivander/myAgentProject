# 模块 1：基础配置与 Agent 管理操作手册

## 1. 文档目的

本文说明模块 1 部署、数据库迁移、环境变量配置、启动、页面配置和验收步骤。

代码已经生成，但以下外部操作尚未由 Codex 执行，必须在你的目标环境完成：

- 备份并迁移实际 MySQL 数据库。
- 生成并安全保存生产加密密钥。
- 使用可访问 Maven 仓库的环境完成标准 Maven 构建。
- 使用 Node.js 20.19+ 或 22.12+ 安装前端依赖并完成构建。
- 启动完整前后端并检查菜单、权限和移动端页面。
- 使用真实 Connector、飞书应用凭据和会话完成外部联调。
- 在真实数据库验证租户、部门数据权限和并发机器人绑定。

## 2. 已生成的交付物

| 内容 | 路径 |
| --- | --- |
| 后端 Agent 配置领域 | `jeecg-boot/jeecg-boot-module/jeecg-boot-module-airag/src/main/java/org/jeecg/modules/airag/agent` |
| Flyway 基础迁移 | `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.3_1__multi_agent_config.sql` |
| V1.1 入口模式迁移 | `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.3_3__multi_agent_bot_entry_mode.sql` |
| 新环境 MySQL 初始化脚本 | `jeecg-boot/db/multi-agent-config.sql` |
| 前端页面 | `jeecgboot-vue3/src/views/super/multiagent` |
| 测试方案 | `tests/【基础配置与Agent管理】_20260713/测试方案.md` |
| 配置说明 | `README-AI.md` |

## 3. 环境要求

建议使用以下版本：

| 组件 | 要求 |
| --- | --- |
| JDK | 17 |
| Maven | 3.9+，并能访问项目配置的 Maven 仓库 |
| MySQL | 与当前 JEECG 环境一致，脚本按 MySQL 5.7+/8.x 编写 |
| Node.js | 20.19+ 或 22.12+ |
| pnpm | 9.x |
| Redis | 项目现有 Redis 5.x；模块 1 本身不使用运行调度 |

确认版本：

```powershell
java -version
mvn -version
node -version
pnpm -version
```

## 4. 必须先完成：数据库迁移

### 4.1 迁移前备份

先备份 `jeecg-boot` 数据库。生产环境不要跳过此步骤。

```powershell
mysqldump -h 127.0.0.1 -P 3306 -u root -p --single-transaction jeecg-boot > jeecg-boot-before-agent-module.sql
```

PowerShell 对 `>` 的编码处理可能因版本不同而变化。生产环境建议使用数据库管理工具或 `cmd.exe` 执行备份命令。

### 4.2 选择正确的迁移方式

当前 profile 的 Flyway 状态：

| Profile | Flyway | 建议 |
| --- | --- | --- |
| `dev` | 默认关闭 | 手工执行增量脚本 |
| `prod` | 默认关闭 | 备份后手工执行增量脚本 |
| `test` | 默认开启 | 可由 Flyway 执行，但仍需检查历史表 |
| `docker` | 默认开启 | 新数据卷还会执行 MySQL 初始化脚本 |

不要把 `db/multi-agent-config.sql` 用作普通 Flyway 脚本。它包含 `USE jeecg-boot`，主要用于新 Docker 数据库初始化。

### 4.3 已有数据库：手工执行增量脚本

如果数据库尚未安装模块 1，按顺序执行三个脚本：

```sql
USE `jeecg-boot`;
SOURCE D:/Project/myAgentProduct/jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.3_1__multi_agent_config.sql;
SOURCE D:/Project/myAgentProduct/jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.3_2__multi_agent_menu_redirect.sql;
SOURCE D:/Project/myAgentProduct/jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.3_3__multi_agent_bot_entry_mode.sql;
```

如果 V1.0 已经运行，只执行 `V3.9.3_3__multi_agent_bot_entry_mode.sql`。不要重复执行不带幂等保护的 V1.1 `ALTER TABLE`。

也可以在 Windows `cmd.exe` 中执行：

```bat
mysql -h 127.0.0.1 -P 3306 -u root -p jeecg-boot < D:\Project\myAgentProduct\jeecg-boot\jeecg-module-system\jeecg-system-start\src\main\resources\flyway\sql\mysql\V3.9.3_1__multi_agent_config.sql
```

V1.1 已有环境命令：

```bat
mysql -h 127.0.0.1 -P 3306 -u root -p jeecg-boot < D:\Project\myAgentProduct\jeecg-boot\jeecg-module-system\jeecg-system-start\src\main\resources\flyway\sql\mysql\V3.9.3_3__multi_agent_bot_entry_mode.sql
```

如果数据库在当前 Compose 容器中，宿主机映射端口是 `13306`：

```bat
mysql -h 127.0.0.1 -P 13306 -u root -p jeecg-boot < D:\Project\myAgentProduct\jeecg-boot\jeecg-module-system\jeecg-system-start\src\main\resources\flyway\sql\mysql\V3.9.3_1__multi_agent_config.sql
```

Compose 默认 root 密码是 `root`。非开发环境必须使用实际密码，不要照搬默认值。

### 4.4 新 Docker 数据卷

`jeecg-boot/db/Dockerfile` 已把 `multi-agent-config.sql` 加入 MySQL 初始化目录。只有首次创建空数据卷时才会执行初始化脚本。

如果数据库容器已经有数据卷，重建镜像或重启容器不会重新运行初始化 SQL，此时仍应按 4.3 手工迁移。

### 4.5 数据库核验

执行以下 SQL：

```sql
USE `jeecg-boot`;

SHOW TABLES LIKE 'ai_%';

SHOW COLUMNS FROM ai_feishu_bot LIKE 'entry_mode';
SHOW COLUMNS FROM ai_feishu_bot LIKE 'command_enabled';

SELECT entry_mode, command_enabled, COUNT(*)
FROM ai_feishu_bot
GROUP BY entry_mode, command_enabled;

SELECT COUNT(*) AS agent_menu_count
FROM sys_permission
WHERE id BETWEEN '2026071300000000001' AND '2026071300000000037';

SELECT id, parent_id, name, url, component, perms
FROM sys_permission
WHERE id BETWEEN '2026071300000000001' AND '2026071300000000037'
ORDER BY id;

SELECT COUNT(*) AS admin_permission_count
FROM sys_role_permission
WHERE role_id = 'f6817f48af4fb3af11b9e8bf182f618b'
  AND permission_id BETWEEN '2026071300000000001' AND '2026071300000000037';
```

预期结果：

- 存在 `ai_agent`、`ai_connector`、`ai_feishu_bot` 三张表。
- 菜单及按钮权限共 25 条。
- 默认管理员角色获得对应的 25 条授权。

## 5. 必须完成：配置凭据加密密钥

### 5.1 生成密钥

推荐使用 OpenSSL：

```powershell
openssl rand -base64 32
```

或者使用 PowerShell/.NET：

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

生成结果是 `AI_CONFIG_SECRET_KEY`。必须满足以下要求：

- Base64 解码后正好 32 字节，或直接使用 32 个 ASCII 字符。
- 开发、重启、扩容后的所有后端实例使用同一密钥。
- 密钥存入部署 Secret 管理系统，不提交到 Git。
- 丢失或更换密钥后，数据库中的现有 Connector 和飞书凭据无法恢复。

### 5.2 本地 PowerShell 临时配置

```powershell
$env:AI_CONFIG_SECRET_KEY = '<生成的 Base64 密钥>'
$env:AI_CALLBACK_BASE_URL = 'http://localhost:8080/jeecg-boot'
$env:AI_AGENT_ALLOWED_HOSTS = '127.0.0.1,localhost,agent.example.com,*.internal.example.com'
$env:AI_EXECUTOR_GATEWAY = 'http'
$env:AI_MOCK_AGENT_ENABLED = 'true'
```

`AI_MOCK_AGENT_ENABLED=true` 只能用于开发或测试环境，生产必须为 `false`。

### 5.3 Docker Compose 配置

在项目根目录创建只用于本机部署的 `.env`，不要提交该文件：

```dotenv
AI_CONFIG_SECRET_KEY=<生成的 Base64 密钥>
AI_CALLBACK_BASE_URL=http://localhost:8080/jeecg-boot
AI_AGENT_ALLOWED_HOSTS=agent.example.com,*.internal.example.com
AI_EXECUTOR_GATEWAY=http
AI_MOCK_AGENT_ENABLED=false
```

可选配置：

```dotenv
AI_FEISHU_API_BASE_URL=https://open.feishu.cn
```

当前 Compose 文件已传递密钥、回调地址、Mock 开关、主机允许列表和执行 Gateway。飞书 API 地址默认使用官方地址；需要模拟飞书服务时，应把该环境变量额外传给后端容器。

### 5.4 主机允许列表规则

`AI_AGENT_ALLOWED_HOSTS` 使用英文逗号分隔：

- `agent.example.com`：只允许精确主机。
- `*.example.com`：允许其子域名，不包含裸域名 `example.com`。
- 不要填写协议、端口或路径。
- 空值会导致 Connector 测试和正式执行 fail closed；至少配置一个可信的精确主机或受控通配规则。

## 6. 后端构建和启动

### 6.1 标准 Maven 验证

确保 JDK 17 生效：

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\ms-17.0.16'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

在后端目录执行：

```powershell
cd D:\Project\myAgentProduct\jeecg-boot
mvn clean install -DskipTests -Pdev
mvn -pl jeecg-boot-module/jeecg-boot-module-airag -am test -Pdev
```

本机此前的标准 Maven 构建受到以下环境问题阻塞，你需要先解决：

- 本地缺少 `jeecg-system-local-api:3.9.3` 时，必须通过 Reactor 完整构建或正确配置 Maven 仓库。
- 项目配置的 JEECG/Nexus 仓库必须可以访问。
- 既有 `jeecg-boot-base-core` 编译警告/编译器资源关闭异常需要在标准构建环境复核。

### 6.2 启动单体后端

推荐在 IDEA 中启动：

```text
org.jeecg.JeecgSystemApplication
```

也可以在完成 Reactor 安装后使用 Maven：

```powershell
cd D:\Project\myAgentProduct\jeecg-boot
mvn -pl jeecg-module-system/jeecg-system-start spring-boot:run -Pdev
```

默认后端地址：

```text
http://localhost:8080/jeecg-boot
```

启动日志中不应出现明文 Connector 密钥、飞书密钥、Authorization 请求头、完整系统提示词或测试输入。

## 7. 前端构建和启动

项目当前依赖 Vite 8，建议使用 Node.js 20.19+ 或 22.12+。本机发现的 Node.js 20.11.1 仍偏低，不建议用于正式构建。

安装 pnpm 9：

```powershell
corepack enable
corepack prepare pnpm@9.15.5 --activate
```

安装依赖并验证：

```powershell
cd D:\Project\myAgentProduct\jeecgboot-vue3
pnpm install --frozen-lockfile
pnpm exec vue-tsc --noEmit
pnpm exec jest
pnpm build
```

启动开发服务器：

```powershell
pnpm dev
```

默认前端地址：

```text
http://localhost:3100
```

开发代理已将 `/jeecgboot` 转发到 `http://localhost:8080/jeecg-boot`。

此前离线依赖安装因缓存缺少 `@ant-design/colors@7.2.1` 而失败，因此你需要在允许访问 npm 镜像的环境执行完整安装。

## 8. 菜单和角色权限

迁移脚本已创建独立根菜单“多 Agent 管理”，包含：

- Agent 管理。
- HTTP Connector。
- 飞书机器人。

默认管理员角色已经由 SQL 授权。其他角色需要你在 JEECG 后台执行：

1. 进入“系统管理 → 角色管理”。
2. 编辑目标角色的菜单权限。
3. 勾选“多 Agent 管理”及需要的子菜单。
4. 按职责分配查询、新增、编辑、删除、启用、禁用、测试按钮权限。
5. 使用该角色账号重新登录，验证页面和按钮是否符合预期。

建议把凭据编辑和连接测试权限只授予可信运维或管理员角色。

## 9. 推荐的首次配置顺序

### 9.1 使用开发 Mock Agent 验证 Connector

前提：后端以 `AI_MOCK_AGENT_ENABLED=true` 启动，并在允许列表中加入 `127.0.0.1` 或 `localhost`。

创建 Connector：

| 字段 | 示例 |
| --- | --- |
| 代码 | `mock_connector` |
| 名称 | `本地 Mock Agent` |
| Base URL | `http://127.0.0.1:8080/jeecg-boot` |
| Path | `/api/ai/mock-agent/execute` |
| 鉴权类型 | `NONE` |
| 成功映射 | `/success` |
| 输出映射 | `/output` |
| 摘要映射 | `/summary` |

操作顺序：

1. 保存 Connector。
2. 点击“测试”，输入 JSON，例如 `{"task":"health check"}`。
3. 确认测试成功且结果预览正常。
4. 点击“启用”。

### 9.2 配置真实 Connector

支持的鉴权类型：

- `NONE`：无鉴权。
- `BEARER`：自动发送 `Authorization: Bearer <secret>`。
- `API_KEY`：使用指定请求头发送密钥，默认请求头是 `X-API-Key`。

Connector 固定发送 POST JSON，不跟随重定向。目标接口需返回符合映射配置的 JSON。

### 9.3 配置飞书机器人（可选）

准备以下飞书应用信息：

- App ID。
- App Secret。
- 可选：机器人有权限发送消息的默认 Chat ID，仅用于主动测试消息和无来源会话的通知。

操作顺序：

1. 在 JEECG 新增飞书机器人，填写 Bot Key、名称、App ID 和 App Secret。
2. 选择入口模式：直连 Agent 使用 `DIRECT_AGENT`，统一命令入口使用 `ORCHESTRATOR`。
3. “仅接收”只记录脱敏元数据；开启“可处理”后事件会移交内部处理器，但模块 1 本身仍不创建运行。
4. 点击“启用”，确认列表中的连接状态由 `STARTING` 变为 `CONNECTED`。
5. 在飞书开放平台的应用“事件与回调”中选择并保存“使用长连接接收事件”。飞书要求先建立 SDK 长连接才能保存该模式。
6. 添加“接收消息 v2.0”事件订阅，并确保应用具备相应消息权限和已发布版本。
7. 如需发送测试消息，再填写默认 Chat ID，点击“测试”并确认目标会话收到消息。

当前通过飞书官方 Java SDK 长连接接收事件，不需要公网回调地址、Verification Token 或 Encrypt Key。`ORCHESTRATOR` 不允许绑定单个 Agent；`DIRECT_AGENT` 保留一个启用机器人最多绑定一个启用 Agent 的约束。事件正文只存在于内部敏感 DTO，不进入 `@AutoLog` 或普通日志。用户绑定、事件幂等、运行创建、指令解析和群内回复仍是后续工作。

官方参考：

- [Java SDK 开发前准备](https://open.feishu.cn/document/server-side-sdk/java-sdk-guide/preparations)
- [Java SDK 处理事件](https://open.feishu.cn/document/server-side-sdk/java-sdk-guide/handle-events)

### 9.4 创建 Agent

操作顺序：

1. 新增 Agent，选择已经启用的 Connector。
2. 根据需要绑定已经启用且凭据完整的飞书机器人。
3. 配置系统提示词、超时 `1..300` 秒和重试 `0..2` 次。
4. 保存后点击“测试”。
5. 测试通过后点击“启用”。

同一个飞书机器人同时只能绑定一个已启用 Agent。

## 10. 凭据更新和清理规则

- 编辑时凭据输入框为空表示保留现有密钥。
- 必须勾选显式清除选项才能删除密钥。
- 已启用配置不能被清除为不完整状态。
- 三类配置必须先禁用再删除。
- Connector 或飞书机器人只要仍被任意 Agent 引用，就不能删除。
- 已启用 Agent 引用 Connector 或机器人时，对应配置不能禁用。

推荐删除顺序：

1. 禁用 Agent。
2. 删除 Agent，或编辑 Agent 解除/替换引用。
3. 禁用飞书机器人和 Connector。
4. 删除飞书机器人和 Connector。

## 11. 验收清单

### 11.1 数据库

- [ ] 三张 `ai_*` 表存在。
- [ ] 25 条菜单/按钮权限存在。
- [ ] 管理员已授权，普通角色权限符合预期。
- [ ] 凭据字段存储的是以 `v1:` 开头的密文，不是明文。
- [ ] 逻辑删除后相同代码无法重新创建。
- [ ] `entry_mode`、`command_enabled` 字段存在，旧机器人默认为 `DIRECT_AGENT` 和仅接收。

### 11.2 后端和安全

- [ ] 缺少 `AI_CONFIG_SECRET_KEY` 时应用仍能启动。
- [ ] 缺少密钥时凭据写入和测试被拒绝。
- [ ] HTTP/HTTPS、用户信息、禁止主机和重定向策略符合预期。
- [ ] 401、500、超时和非法 JSON 返回脱敏错误。
- [ ] 跨租户、跨部门和篡改 ID 无法读取或修改资源。
- [ ] 20 个并发 Agent 启用请求绑定同一机器人时最多一个成功。
- [ ] 跨租户/部门调用 `/api/ai/agents/options` 不返回不可见 Agent。
- [ ] `ORCHESTRATOR` 可无 Agent 启用，且不能绑定或被切换为仍有 Agent 引用的模式。
- [ ] 慢速或异常内部处理器不阻塞飞书 SDK 接收线程。

### 11.3 日志脱敏

使用专门的测试字符串作为临时密钥，例如 `MODULE1_LEAK_CHECK_ONLY`，完成一次失败测试后查询：

```sql
SELECT id, log_content, request_param, create_time
FROM sys_log
WHERE create_time >= NOW() - INTERVAL 1 DAY
  AND (
    log_content LIKE '%MODULE1_LEAK_CHECK_ONLY%'
    OR request_param LIKE '%MODULE1_LEAK_CHECK_ONLY%'
  );
```

预期返回 0 条。测试结束后立即替换该临时凭据。

### 11.4 前端

- [ ] 桌面端三个列表、抽屉、标签页、测试弹窗显示正常。
- [ ] 移动端抽屉不超出视口，表单字段按单列排列。
- [ ] 编辑详情不回填任何密钥。
- [ ] 启停和删除有确认提示。
- [ ] 飞书列表显示入口模式、连接状态和仅接收/可处理状态。
- [ ] 列表显示启用状态和最近测试状态。

## 12. 回滚建议

优先通过数据库备份恢复。只有确认模块数据不再需要时，才考虑手工删除表和菜单。

破坏性回滚顺序：

```sql
DELETE FROM sys_role_permission
WHERE permission_id BETWEEN '2026071300000000001' AND '2026071300000000037';

DELETE FROM sys_permission
WHERE id BETWEEN '2026071300000000001' AND '2026071300000000037';

DROP TABLE IF EXISTS ai_agent;
DROP TABLE IF EXISTS ai_feishu_bot;
DROP TABLE IF EXISTS ai_connector;
```

执行前必须备份数据库。若 Flyway 已记录该迁移，还需要由数据库管理员同步处理 `flyway_schema_history`，不要直接删除历史记录后继续启动生产服务。

## 13. 当前主要风险

- 加密密钥丢失或不同实例使用不同密钥，会导致已有凭据无法解密。
- Connector 允许列表为空时测试和正式执行都会 fail closed；漏配会导致全部外部模型调用失败。
- 已有 Docker 数据卷不会重新执行 MySQL 初始化脚本，容易漏迁移。
- 菜单 SQL 只自动授权默认管理员，其他角色需要人工授权。
- 真实飞书测试依赖应用权限、外网、Chat ID 和租户令牌配置。
- 前端生产构建已在本机通过；标准 Maven 仍受既有 javac 资源关闭异常影响，真实数据库并发验收仍需在发布前补做。

## 14. 模型 Connector 配置与使用

### 14.1 数据库和环境

已有数据库在 `_1` 至 `_6` 后执行 `V3.9.3_7__multi_agent_connector_provider.sql`；新数据库使用已同步的 `jeecg-boot/db/multi-agent-config.sql`。真实调用必须同时配置稳定的 `AI_CONFIG_SECRET_KEY` 和非空 `AI_AGENT_ALLOWED_HOSTS`。

### 14.2 推荐配置流程

1. 新增“模型 Connector”，先选择 Provider。
2. 系统自动填充官方 Base URL、Path、鉴权和必要 Header；私有网关只修改确有差异的字段。
3. 填写 Model Name 和凭据。Ollama 无需凭据，但本机地址仍须加入 allowlist。
4. 普通文本任务选择“纯文本”；需要人工介入、Artifacts 或失败重试语义时选择“Result 1.1”。
5. 保存后先执行“测试”，测试成功再启用 Connector 和引用它的 Agent。

Provider 切换不会回显或自动清除已保存密钥。空值或旧 Provider 默认值会更新为新预设；自定义地址、Path、鉴权或 Header 会保留并显示“已保留自定义值”，必须重新测试确认它们仍适用于新 Provider。

### 14.3 Provider 差异

| Provider | 默认地址与接口 | 凭据 |
| --- | --- | --- |
| OpenAI-compatible | `https://api.openai.com/v1/chat/completions` | Bearer Token |
| DeepSeek | `https://api.deepseek.com/chat/completions` | Bearer Token |
| Anthropic | `https://api.anthropic.com/v1/messages` | `x-api-key`；系统补充 `anthropic-version` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` | `x-goog-api-key` |
| Ollama | `http://127.0.0.1:11434/api/chat` | 无鉴权 |
| Custom | 用户配置 | 沿用 LEGACY 或 Result 1.1 |

高级配置只允许 `temperature` 0 至 2、`topP` 0 至 1、`maxTokens` 1 至 65536。未填写时不发送；Anthropic 因接口必填会使用 `maxTokens=4096`。未知参数由后端拒绝，不能通过 JSON 任意透传厂商参数。

### 14.4 结果与安全边界

- `TEXT` 只生成 `SUCCESS`、`needsUser=false`、`retryable=false` 和 `output.text`；空文本返回 `MODEL_RESPONSE_EMPTY`。
- `RESULT_1_1` 必须返回无 Markdown 围栏的严格 Agent Result 1.1 JSON，并通过现有 Validator。
- 测试与正式 Pipeline 共用相同 Adapter、超时、1 MiB 上限、无重定向、allowlist、DNS 和脱敏策略。
- 模型原始响应、密钥、鉴权 Header 和完整系统提示词不得写入日志或运行快照。
- 首期不支持流式响应、Tool Calling、视觉或音频输入。
