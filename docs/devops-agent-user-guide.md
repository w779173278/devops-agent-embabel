# DevOps Agent 使用指南

本文档帮助你在本地快速运行 DevOps Agent，并梳理一次完整的 Agent 调用流程（诊断→规划→执行），便于在中文环境下演示或二次开发。

## 1. 运行方式

1. **准备环境**
   - 配置 `OPENAI_API_KEY` 环境变量（默认为 DeepSeek Chat，使用其他模型可修改 `application.yml`）。
   - JDK 21+ 与 Maven Wrapper 已包含在仓库中。
2. **启动服务**
   ```bash
   ./mvnw -pl devops-agent spring-boot:run
   ```
   服务启动后默认监听 `http://localhost:8080`，可通过 `/actuator/health` 检查状态。

## 2. DDD 分层结构

`devops-agent/src/main/kotlin/com/embabel/devops` 采用 DDD 风格的分层目录：

- `agent/`：应用服务与多 Agent 编排（`DiagnosisAgent`、`ActionPlannerAgent`、`DiagnosisWorkflow`、`RemediationWorkflow`、会话记忆等）。
- `model/`：领域模型、聚合与仓储（诊断/修复数据类、`ActionPlanService`、`DiagnosisService`、Repository）。
- `tool/`：对接外部系统的工具实现（日志/指标采集、修复计划与执行器、确认/数据源适配器等）。
- `web/`：REST 控制器，负责 HTTP DTO 与领域模型的映射。
- `config/`：Spring 配置与属性映射。

这种分层便于区分对话编排（Agent 层）、领域核心（Model 层）与基础设施（Tool/Web 层），扩展数据源或接入新的 Agent 时只需针对对应的目录修改。

## 3. 诊断接口

调用 `POST /v1/diagnostics` 可触发一次对话式诊断，示例：

```bash
curl -X POST http://localhost:8080/v1/diagnostics \
  -H "Content-Type: application/json" \
  -d '{
        "conversationId": "chat-001",
        "service": "checkout",
        "environment": "prod",
        "windowMinutes": 15,
        "symptom": "HTTP 5xx error spike"
      }'
```

响应体包含：
- `diagnosisId`：后续修复所需的诊断编号。
- `issues`：命中的规则（取自 `diagnostics.yml`），每条包含严重级别、摘要与证据。
- `suggestedActions`：可交给行动规划 Agent 的动作 ID。
- `summary`、`progress`：`DiagnosisAgent` 输出的中文摘要与进度播报。

可通过 `GET /v1/diagnostics/{diagnosisId}` 查询历史诊断。

## 4. 修复接口

当用户确认某个动作后，调用 `POST /v1/actions/execute`：

```bash
curl -X POST http://localhost:8080/v1/actions/execute \
  -H "Content-Type: application/json" \
  -d '{
        "conversationId": "chat-001",
        "diagnosisId": "d-123",
        "actionId": "restart-checkout",
        "ticketId": "INC-10086",
        "confirmationPhrase": "CONFIRM_RESTART_CHECKOUT",
        "mode": "DRY_RUN"
      }'
```

接口会返回 `executionId` 与命令输出。若需要正式执行，将 `mode` 改为 `EXECUTE` 并重新提供口令。

## 5. Agent 调用流程概览

1. **DiagnosisWorkflow**
   - `LogFetchTool`/`MetricsFetchTool` 根据 `application.yml` 中的数据源拉取观测值，生成 `ObservationBundle`。
   - `DiagnosisAgent.collectSignals` 汇总数据，`DiagnosisService.aggregate` 按 `diagnostics.yml` 的规则匹配异常并写入仓库。
   - `DiagnosisAgent.summarize` 给出中文摘要，并在 `ConversationMemoryService` 中记录会话上下文。
2. **RemediationWorkflow**
   - `ActionPlannerAgent.proposePlan` 通过 `ActionPlanService` 将诊断映射到 `actions.yml` 中的修复步骤。
   - `ActionPlannerAgent.confirmPlan` 先调用 `ActionExecutorTool` 以 `dryRun` 模式预演，`RemediationConfirmationService` 负责校验确认口令。
   - `ActionPlannerAgent.executePlan` 执行真实命令并写入 `ExecutionRecordRepository`，必要时可调用 `rollback`。

> 小贴士：`conversationId` 串联了上述所有子流程，Agent 会在记忆中记录最近一次诊断 ID 和执行过的动作，方便继续对话。

## 6. 自定义提示与配置

- **提示词与播报**：`DiagnosisWorkflow`、`DiagnosisAgent` 和配置文件中的文案均已本地化为中文，可根据业务语气进一步调整。
- **规则/动作**：`diagnostics.yml` 与 `actions.yml` 中的 `description` 直接影响用户可见的中文提示。
- **数据源**：在 `application.yml` 的 `devops.data-sources` 中追加新的日志/指标源描述即可被 `LogFetchTool`/`MetricsFetchTool` 自动发现。

借助以上流程，您可以在中文对话环境中向 Agent 询问告警、追踪诊断，并在人工确认下安全执行修复动作。
