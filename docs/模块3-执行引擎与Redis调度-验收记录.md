# 模块 03 执行引擎与 Redis 调度验收记录

## 实施基线

- 规格：`03-执行引擎与Redis调度计划-V1.2-可实施正式版.docx`
- 契约：`PipelineDefinition 1.1`、`AgentResultContract 1.1`
- 技术边界：MySQL 权威状态、事务 Outbox、Redis 5 Streams、应用内最大五并发
- 未修改现有 AIFLOW、`airag_flow`、`/airag/flow`、LiteFlow 或前端文件

## 已交付内容

- Module 02 封口：授权版本查询、运行前依赖复核、Result/Artifact 严格校验。
- 数据库：Flyway `_5` 与 fresh-install SQL，包含七张运行表、生成列唯一键、索引和五个运行权限。
- 执行引擎：PIPELINE/AGENT_DIRECT 创建、请求与飞书事件幂等、运行快照、DAG 汇聚、五种节点 Handler。
- 人工操作：NEEDS_INPUT、重试耗尽、同 run/node 恢复、取消、失败运行新 run 重试、依赖禁用取消。
- 调度恢复：Outbox claimToken、Redis Group、ACK 边界、XPENDING/XCLAIM、MySQL lease、dispatchVersion 去重。
- API：运行列表、详情、事件、取消、重试、介入、Artifact 和摘要；请求未知字段强制拒绝。
- 配置：dev 使用 Mock Gateway；`ai.executor.gateway=http` 启用统一 Custom/模型 HTTP Gateway，缺省仍为 disabled。

## 验证结果

- 定向 Module 02/03 Surefire：16 个测试，14 通过，0 失败，2 跳过。
- 跳过项：`RunEngineMysqlIntegrationTest` 缺少 `RUN_TEST_MYSQL_*`；`RunEngineRedisIntegrationTest` 缺少 `RUN_TEST_REDIS_*`。
- 全 airag 已编译回归：47 个测试，43 通过，1 个错误，3 跳过。
- 既有错误：`AiFeishuBotModeServiceTest.orchestratorCanBeEnabledWithoutAgentBinding` 缺少 HTTP 数据权限上下文，与实施前基线一致。
- 主源码和测试源码均已生成 class 文件；Maven 编译阶段仍因既有 javac“无法关闭编译器资源”返回失败，没有 Module 03 类型诊断。
- `git diff --check` 通过；禁止依赖与敏感日志扫描未发现 Module 03 命中。
- SQL 静态核对：Flyway 与 fresh-install 均为七表、五权限。

## 外部验收待办

- 配置真实 MySQL 5.7+ 环境后执行 `_4 + _5`、事务回滚和生成列唯一键测试。
- 配置真实 Redis 5 后执行 Group、pending、claim、ack、断连和重启恢复测试。
- 配置真实 Provider、密钥和 allowlist 后验证模型 Agent 与飞书闭环。
- Module 05 只消费脱敏 API，禁止直接展示历史 `definition_json`。

## 主要风险

- 外部 Agent 不具备绝对 exactly-once；稳定 invocationId 只提供可重放幂等基础。
- Redis ACK 与 MySQL lease 的边界最容易产生重复外部调用，Module 04 必须原样透传 invocationId。
- 本机未提供真实 MySQL/Redis，因此数据库并发和 Redis 恢复结论仍需外部环境证据。
- 现有 Maven 编译器资源关闭故障会让标准生命周期返回失败，应依据源码诊断和分离 Surefire 结果判断。
- 后台线程的租户恢复、运行快照脱敏和依赖查询必须在后续模块回归中持续检查。

## 模型调用 Gateway 增补（2026-08-10）

- 执行引擎继续只依赖 `AgentExecutionGateway` 和 `AgentResultContract`，不包含 Provider 分支。
- `HttpAgentExecutionGateway` 把稳定 `invocationId` 作为内部 `ModelCallRequest.requestId`，并透传 input、resumeInput 和 Artifact descriptors。
- 模型 TEXT 与 RESULT_1_1 在 Gateway 边界转换为 Agent Result 1.1 后进入现有 Validator、重试、WAITING 和 Artifact 流程。
- MySQL 权威状态、Outbox、Redis 5 Streams、lease、取消和介入恢复规则未改变。
- 本地 Provider/Gateway 定向测试使用 Mock HTTP Server；真实外部模型、MySQL、Redis 和飞书证据仍需分开报告。
