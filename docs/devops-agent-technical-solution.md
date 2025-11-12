# DevOps AI Agent 技术方案（对话式日志诊断场景）

## 1. 概述
本方案依据《DevOps AI Agent 对话式功能分析》制定，聚焦“用户以自然语言对话完成日志/指标诊断与修复协作”的技术落地路径。目标：
- 让值班工程师仅通过聊天描述问题即可触发诊断，Agent 负责意图解析、参数补齐和进度播报。
- 保持与原 MVP 相同的数据与执行能力（日志、指标、规则、修复命令），但将它们包装为对话可编排的 Tool。
- 提供可插拔 MCP 数据源与 Action Tool，确保同一套能力既能在对话内被调用，也能暴露给外部 Agent。
- 以对话上下文记忆、确认与审计机制为核心，保证多轮会话中的安全性和可追溯性。

## 2. 架构总览
围绕“聊天即指令”的交互方式，系统自顶向下划分为：
1. **对话交互层**：来自聊天界面（Web/CLI）或外部 Agent 的自然语言请求通过 REST/MCP 进入系统，统一由 Session Router 维护会话状态。该层负责 Token 校验与多轮上下文跟踪。
2. **对话编排层（Embabel Agent）**：Embabel Agent Runtime 负责将用户意图解析为标准诊断/执行任务，必要时追问缺失参数，并串联日志、指标、修复 Tool；Conversation Memory（Redis/内存）用于存储已确认的服务、时间窗口、诊断 ID。
3. **工具与修复层**：包括 Log/Metrics Fetch Tool、Diagnosis Tool、Remediation Planner、Action Executor 等，这些 Tool 同时通过 MCP 暴露给外部系统，复用 JSON Schema 入参。
4. **数据源与执行适配层**：实现日志、Prometheus、命令执行等适配器，向 Tool 层提供标准化对象；支持热插拔 MCP Resource Provider 以注册新数据源。
5. **支撑能力层**：安全、配置、审计、监控、存储等通用能力。

```
+-----------------------------------------------------------------------+
|                           对话交互层                                  |
|  - REST Chat API  - MCP Endpoint  - Session Router (tokens, locale)   |
+-------------------------------+---------------------------------------+
|        对话编排层（Embabel Agent Runtime）   |  Tool 调度 & 记忆       |
|  - Intent Parser / Prompt      |  - Conversation Memory (Redis)       |
|  - Clarification Workflow      |  - Progress Notifier                 |
+-------------------------------+---------------------------------------+
|             工具与修复层（Embabel Tool Adapter + MCP Tool）           |
|  - LogFetchTool   - MetricsFetchTool   - DiagnosisTool                |
|  - RemediationPlannerTool      - ActionExecutorTool   - RollbackTool  |
+-------------------------------+---------------------------------------+
|                数据源与执行适配层                                     |
|  - MCP Resource: Logs/Metrics   - Prometheus Client                   |
|  - HTTP/File Collector          - Command Adapter (SSH/kubectl)       |
|  - YAML 配置仓库                - Export Storage                      |
+-------------------------------+---------------------------------------+
|                         支撑能力层                                   |
|  配置管理 | 安全认证 | 审计日志 | 监控与追踪 | 存储与缓存            |
+-----------------------------------------------------------------------+
```

## 3. 对话式交互能力实现
围绕需求文档中描述的五大对话流，系统提供以下技术支撑：
- **多轮上下文管理**：Session Router 为每个会话维护上下文 State（服务、环境、时间范围、诊断/动作 ID）。Embabel Conversation Memory 以 key-value 形式存储，并暴露给 Tool 作为隐式入参。
- **意图解析与追问**：Prompt 模板引导 LLM 输出结构化 JSON，包括 `intent`、`missing_fields`、`clarifications`。当缺少关键参数时，Clarification Workflow 会在聊天中提问，直到上下文满足 Tool schema。
- **进度播报与证据解释**：Tool 调用前后由 Progress Notifier 生成可读语句（“正在获取 prod-checkout 日志…”），DiagnosisTool 输出包含摘要、影响、证据列表，Agent 再转换成自然语言描述。
- **安全确认与干跑**：ActionExecutorTool 暴露 `mode=dry_run|execute`，并要求用户提供显式确认短语。Agent 在记忆中记录最近一次确认内容及有效期，防止误触发。
- **审计与导出**：每次会话中的诊断和执行记录都绑定 `conversationId`，ExportTool 可按需输出 JSON 并回传下载链接或存储位置。

## 4. 关键技术选型
| 能力 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 3.3.x | 提供基础运行时，启用 WebFlux 与 Actuator |
| AI 能力 | Embabel Agent Runtime | 基于 `embabel/embabel-agent` 提供的多 Agent 编排（参考 `kotlin-agent-template` 与 `embabel-agent-examples`）集成 LLM 与上下文记忆 |
| 工具封装 | Embabel Tool Adapter + MCP | 通过 Embabel Tool API 定义诊断/修复动作，并同步暴露为 MCP Tool 以统一审批、审计 |
| 数据扩展 | MCP Resource Provider | 以 resource 形式注册日志、指标、事件等数据源，实现热插拔扩展 |
| 第三方集成 | Model Context Protocol Server | 支持通过 MCP 与外部日志/指标系统或企业 Agent Hub 对接 |
| 数据采集 | Reactor + WebClient / NIO File Watcher | 非阻塞采集日志与指标 |
| 缓存/记忆 | Redis（可选） | 存储对话上下文、诊断结果缓存 |
| 日志 | Logback + JSON Layout | 结构化日志，便于分析 |
| 配置 | Spring Boot Config + YAML | 数据源、阈值、命令定义 |

## 5. 模块设计
### 5.1 数据源适配层（开放式 MCP 采集）
- **MCP DataSource Provider**
  - 每一种日志/指标/事件源实现 `McpDataSourceProvider` SPI，声明 `resource` 名称、入参 Schema 以及输出 payload。
  - 通过配置即可注册新 Provider（如 Trace、APM、工单系统），无需修改代理核心代码，达到“即插即用”。
  - Provider 可托管在独立 MCP Server，也可内嵌在本服务中，统一通过 Embabel `McpClient` 获取数据。
- **LogCollector**
  - `HttpLogCollector`：基于 WebClient 周期性拉取远程日志（支持 NDJSON）。
  - `FileTailCollector`：使用 NIO WatchService 读取本地/挂载文件的增量日志。
  - 通过 `LogIngestionService` 聚合并写入内存缓冲（RingBuffer）。
- **MetricsCollector**
  - `PrometheusQueryClient`：封装 `/api/v1/query` 请求，支持自定义查询表达式。
  - `MetricsIngestionService`：按配置窗口拉取指标数据，输出标准化结构。
- 数据统一映射为 `Observation` 对象：`{timestamp, sourceType, sourceId, severity, payload}`，并可通过 MCP 资源接口暴露给其它智能体重用。

### 5.2 工具与修复层（Tooling & Remediation）
- **Tool 职责定位**：Tool 只用于“计划—审批—执行”阶段，确保修复动作在统一入口被审计；日志与指标数据则通过 MCP 资源提供，避免读写能力混用。
- **核心 Tool**
  - `RemediationPlannerTool`：根据诊断结果挑选候选动作、生成执行方案，并输出风险/依赖说明。
  - `ActionExecutorTool`：绑定白名单命令模板，执行 SSH/kubectl/REST 操作，记录输出。
  - `RollbackTool`：当执行失败或人工要求回滚时，触发对应的回滚命令及监控验证。
- **MCP Tool Adapter**：上述 Tool 通过 MCP `tool` 声明暴露，输入输出都带版本化 Schema，方便其他智能体触发修复动作或复用执行结果。
- **上下文管理**：Tool 执行前后会把诊断 ID、Action ID、审批记录写入 `RemediationContext`，Agent 通过此上下文持续跟踪状态。
- **整体闭环**：日志/指标等数据经 MCP Provider 注入 Agent → Agent 生成诊断 → 调用修复类 Tool 完成计划、审批、执行 → 结果同时回写 REST 与 MCP 客户端。

### 5.3 智能代理层（Embabel Agent）
- **Agent Orchestrator**
  - 基于 `embabel/embabel-agent` 的运行时构建诊断/修复 Agent，沿用 `embabel/kotlin-agent-template` 中的 `AgentRuntimeBuilder`、`GraphExecutor` 等组件来描述多步骤流程。
  - Agent 角色示例：
    - `diagnosisAgent`: 聚合来自 MCP 数据源的日志、指标与配置快照，驱动规则/LLM 分析生成诊断报告。
    - `actionPlannerAgent`: 基于诊断结果和策略库挑选动作，串联 `RemediationPlannerTool → ApprovalValidatorTool → ActionExecutorTool` 完成执行。
- **Prompt 与技能包装**
  - 复用 `embabel-agent-examples` 中的 Prompt/Skill 组织方式，将对话上下文、输入输出 Schema 通过 Embabel `PromptTemplate`/`SkillDefinition` 管理。
  - 每个 Tool 以 Embabel `ActionProvider` 注入，便于在对话中被调用或暴露给外部 Agent。
- **内存管理**
  - 借助 Embabel `ConversationStateStore` 对接 Redis 或 In-Memory 缓存，管理最近一次诊断上下文。
- **推理流程（示例）**
  1. REST/MCP 入口触发诊断。
  2. Agent 创建会话，通过 MCP `logs/*` Resource 拉取指定窗口日志片段。
  3. 调用 MCP `metrics/*` Resource 获取指标趋势/聚合数据。
  4. 将多源 Observation 输入规则引擎，产出诊断与置信度。
  5. 若需要修复，调用 `RemediationPlannerTool` → `ApprovalValidatorTool` → `ActionExecutorTool`/`RollbackTool` 完成执行闭环。
  6. 诊断与执行结果写入 Conversation Memory，并反馈给 REST/MCP 客户端。

### 5.4 服务交互层
- **REST API（Spring WebFlux）**
  - `POST /v1/diagnostics`：参数包括时间窗口、数据源过滤、输出格式。
  - `GET /v1/diagnostics/{id}`：查询历史结果。
  - `POST /v1/actions/execute`：传入诊断 ID、动作 ID、审批令牌。
- **MCP Endpoint 集成**
  - 通过第三方 MCP Server 将上述 API 暴露为标准化 `resource`（数据）与 `tool`（修复），供企业内其它智能体或对话前端调用。
  - MCP 会话中，日志/指标请求落在数据源 Provider，修复请求调用 Tool，保持 JSON Schema 一致，便于在多 Agent 编排中串联。
  - 典型流程：外部 LLM → MCP Server → Embabel Agent → MCP 数据源（logs/metrics）→ 修复 Tool → 结果回写 MCP。

## 6. 核心业务流程
### 6.1 诊断流程（时序）
1. 用户通过 REST 调用或第三方 MCP 会话请求诊断。
2. `DiagnosisService` 根据参数创建任务 → 调度 Agent。
3. Agent 调用 MCP 数据源 Provider 获取日志、指标、配置等 Observation，并驱动规则/LLM 分析。
4. Agent 汇总为标准诊断实体 `DiagnosisResult`（含问题摘要、证据、建议、置信度）。
5. 结果写入内存存储，并回传给调用方。

### 6.2 修复执行流程
1. 用户在诊断结果中选择 `ActionPlan`。
2. `ActionService` 校验白名单与审批令牌。
3. `RemediationPlannerTool` 生成计划 → `ApprovalValidatorTool` 完成审批 → `ActionExecutorTool` 执行命令（可 dry-run），并捕获退出码、输出、耗时。
4. 更新 `ActionExecutionRecord`，并通过 REST/MCP 返回。

## 7. 数据模型草案
- `DataSourceConfig`：`id, type(LOG/METRICS), endpoint, auth, pollingInterval, query`。
- `Observation`：`timestamp, sourceId, severity/type, payload(JSON)`。
- `DiagnosisRequest` / `DiagnosisResult`：包含窗口、异常列表、建议操作、生成时间。
- `ActionDefinition`：`id, name, commandTemplate, preConditions, rollbackCommand`。
- `ActionExecutionRecord`：`id, diagnosisId, actionId, status, output, executor, executedAt`。

## 8. 配置与部署
- 采用 Spring Boot 配置体系：
  - `application.yml`: 基础配置 + 数据源列表。
  - `diagnostics.yml`: 阈值、关键词、规则定义。
  - `actions.yml`: 修复命令、白名单路径、回滚指令。
- 支持 Docker 部署：提供 Dockerfile 与 docker-compose，用于本地演示。
- 可选 Helm Chart，部署至 Kubernetes。
- 需要 Redis（可选）、Prometheus（现有或模拟）、日志源模拟器。


## 10. 可观测性
- 使用 Spring Actuator 暴露 `/actuator/health`、`/actuator/metrics`、`/actuator/loggers`。
- 集成 Micrometer + Prometheus 导出 Agent 自身指标（诊断耗时、Tool 调用次数、执行成功率）。
- 使用 OpenTelemetry（可选）对诊断流程/命令执行链路进行追踪。

## 11. 迭代与扩展方向
- 引入更多 MCP 数据源 Provider：事件告警、配置变更记录、APM 数据。
- 增加自动学习功能：基于历史诊断结果优化规则。
- 引入多步修复编排与审批流程（集成 BPM/工单系统）。
- 支持多 Agent 协作：诊断 Agent + 验证 Agent + 修复 Agent。
- 在 MCP 层对接外部知识库与文档检索，增强建议质量。

## 12. 工期与任务拆解（建议）
1. 基础框架搭建（Spring Boot + WebFlux + 安全）—— 1 周。
2. 数据源适配与标准化模型实现—— 1.5 周。
3. Embabel Agent & Tool 集成（诊断流程）—— 1.5 周。
4. 修复动作执行与审计机制—— 1 周。
5. REST/MCP 接口联调 + 验收用例—— 0.5 周。
6. 部署与文档（Docker/配置指南）—— 0.5 周。

> *总计约 6 周，可根据团队资源并行推进。*

## 13. 风险与缓解
- **LLM 可用性**：针对外部 API 限制，预留本地模型或缓存策略。
- **命令执行安全**：强制白名单 + 干跑模式 + 审批令牌，防止误操作。
- **数据延迟**：对于高吞吐日志需评估缓存策略与背压机制。
- **诊断准确性**：使用单元测试 + 回归数据验证规则，逐步引入更复杂模型。

---
本技术方案为 MVP 实施提供清晰架构依据，可在后续迭代中逐步扩展数据源、智能程度与自动化范围。
