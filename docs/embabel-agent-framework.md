# Embabel Agent 框架介绍

Embabel Agent（https://github.com/embabel/embabel-agent）是一个基于 Spring 生态的多智能体运行时，聚焦“可观测、可控、可扩展”的企业级代理应用。它提供 Shell、MCP Server、MCP Client 等多种运行模式，并通过注解或 DSL 方式构建可复用的 Agent、Action 与 Tool，从而在 Kotlin/Java 项目中快速搭建 LLM 驱动的业务工作流。

## 1. 核心能力速览
| 能力块 | 说明 |
|--------|------|
| Agent Runtime | `AgentRuntimeBuilder`、`GraphExecutor` 等组件负责多步骤推理、任务计划和上下文路由，内建事件流可追踪每个 Action 的输入输出。|
| Spring Boot 集成 | 通过 `@EnableAgentShell`、`@EnableAgentMcpServer`、`@EnableAgents` 等注解将代理注册为 Spring Bean，继承配置管理、DI、安全与监控能力。|
| 双语言支持 | Kotlin/Java 同步支持，示例工程提供两套实现，便于迁移现有 JVM 代码。|
| Tool & Skill 体系 | 使用 `@Action`、`@AchievesGoal` 或 DSL `agent { flow { ... } }` 定义技能，支持工具分组、进度播报、上下文 BlackBoard 模式。|
| MCP 互操作 | 既可将本地 Agent 以 MCP Tool 形式暴露，也可以挂载外部 MCP Server（例如 Docker Desktop）中的资源、命令。|
| 主题化日志 | 运行时内置多种 Logging Theme（Star Wars、Severance 等），便于在开发阶段快速识别不同 Agent 的执行轨迹。|

## 2. 运行模式
1. **Agent Shell 模式**：`@EnableAgentShell` + `@EnableAgents`，提供交互式 CLI，适合开发与调试。脚本示例：`scripts/kotlin/shell.sh`。
2. **Shell + MCP Client 模式**：在 Shell 基础上配置 `mcpServers = [McpServers.DOCKER_DESKTOP]`，允许代理直接调用外部 MCP 工具（Docker、内部资源等）。
3. **MCP Server 模式**：`@EnableAgentMcpServer` 让 Embabel 进程以 MCP 服务端形式运行，把自定义 Agent 暴露给其它 LLM/Agent 平台，通过 SSE 提供工具目录与调用链路。

## 3. 核心注解与实现方式
### 3.1 `@EnableAgentShell`
- **作用**：启用交互式 Shell（CLI）入口，自动注册 `CommandLineRunner` 将所有 `@Agent` 暴露为可执行指令，并输出 Embabel 的进度日志。
- **用法示例**：
  ```kotlin
  @SpringBootApplication
  @EnableAgentShell
  @EnableAgents
  class DevOpsAgentShellApplication
  ```
- **实现要点**：
  1. 保证依赖中包含 `embabel-agent-spring-boot-starter`。
  2. 运行 `./gradlew bootRun --args='shell'` 或使用 `scripts/kotlin/shell.sh`，启动后可通过 `x "诊断 checkout 服务"` 直接触发 Agent。
  3. 可以在 `application.yml` 中配置 `embabel.shell.history=true` 等属性以持久化历史指令。
  4. **DevOps 实践**：在本项目中可将 Shell 模式与 `SPRING_PROFILES_ACTIVE=local` 绑定，方便值班工程师在本地复盘日志、验证工具链。

### 3.2 `@EnableAgentMcpServer`
- **作用**：将当前 Spring Boot 应用暴露为 MCP Server，自动注册 JSON-RPC/SSE 通道以及 Tool Catalog。
- **用法示例**：
  ```kotlin
  @SpringBootApplication
  @EnableAgentMcpServer
  @EnableAgents(mcpServers = [McpServers.DOCKER_DESKTOP])
  class DevOpsAgentMcpServerApplication
  ```
- **实现要点**：
  1. MCP Server 会监听配置的端口（默认 8080，可在 `application.yml` 下 `embabel.mcp.server.port` 调整）。
  2. 需要在部署环境开放 SSE 所需的网络出口，并配置认证（可自定义 `AuthenticationProvider`）。
  3. 当 `@Action` 带有 `toolId` 或默认方法名时，会自动出现在 MCP Tool 列表。
  4. **DevOps 实践**：上线前可通过 `curl -N http://host:port/mcp` 验证 SSE 通道是否正常推送 Tool Catalog，再将地址注册进企业 Agent Hub。

### 3.3 `@EnableAgents`
- **作用**：扫描项目中的 `@Agent` 定义，挂载日志主题、外部 MCP Server 适配器以及共享的 Conversation Memory。
- **常用属性**：
  - `loggingTheme`: 选择日志风格，如 `LoggingThemes.STAR_WARS`、`LoggingThemes.SEVERANCE`。
  - `mcpServers`: 指定可连接的外部 MCP Server，例如 `McpServers.DOCKER_DESKTOP`。
  - `conversationStore`: 可选自定义 `ConversationStateStore` Bean，用于 Redis 等外部存储。
- **实现要点**：可在不同 `@SpringBootApplication` 启动类上配置不同的 `@EnableAgents` 参数，用于区分本地调试与生产部署。
  - **DevOps 实践**：在生产模式下可将 `loggingTheme` 统一设置为 `LoggingThemes.SEVERANCE`，同时通过 `mcpServers` 注入受控的 Docker/K8s 工具集，避免调试工具误暴露。

### 3.4 `@Agent`
- **作用**：声明一个具名 Agent，框架会将其注册为 Spring Bean，并在 Shell/MCP 中暴露。
- **用法**：
  ```kotlin
  @Agent(name = "diagnosisAgent", description = "诊断日志与指标异常")
  class DiagnosisAgent(
      private val logTool: LogFetchTool,
      private val metricsTool: MetricsFetchTool
  ) {
      @Action fun pullSignals(input: DiagnosisInput): Observations { ... }
  }
  ```
- **实现要点**：
  - 类可以使用构造函数注入 Spring Service/Tool。
  - 支持组合 `@Condition`、`@RequiresConfirmation`（如自定义注解）来控制 Action 流程。

### 3.5 `@Action`
- **作用**：将方法注册为可执行的 Tool/技能，支持参数绑定、输入验证、输出上下文存储。
- **关键属性**：
  - `toolGroups`: 指定所属工具组，结合 `CoreToolGroups.WEB`、`KUBERNETES` 等用于授权控制。
  - `outputBinding`: 将返回值命名后写入黑板，在后续 Action 中通过 `@Input("bindingName")` 读取。
  - `mode`: 可配置同步/异步执行策略（依赖框架版本）。
- **实现要点**：
  - 方法入参可以是 DTO、表单对象或 Embabel 提供的 `UserInput`。
  - 如果需要访问外部 LLM，直接注入 `ChatModel` 或调用封装 Service。
  - 在 DevOps 场景中可将现有 `DiagnosisService` 包装成 `@Action`，以复用逻辑。
  - **进阶技巧**：通过 `ActionContext` 访问黑板数据或注入 `ToolProgressPublisher` 主动播报执行阶段；结合 `@Condition`（布尔方法）可以对 Action 进行执行前判定。

### 3.6 `@AchievesGoal`
- **作用**：标记达成用户目标的最终 Action，便于框架在 Shell/MCP 中汇报“完成”的事件，并触发审计/导出流程。
- **实现方式**：
  ```kotlin
  @AchievesGoal(description = "完成诊断报告输出")
  @Action
  fun summarize(observation: Observations): DiagnosisResult {
      // 生成摘要 + 建议
  }
  ```
  通常配合 `outputBinding` 把最终结果写回上下文，以便 REST 层或 MCP Endpoint 返回。

### 3.7 其他常见注解与绑定
- **`@Condition`**：放在返回 `Boolean` 的方法上，用于决定某个 Action 是否执行。可用于“仅当指标异常”才触发修复工具。
  ```kotlin
  @Condition("onlyWhenErrorRateHigh")
  fun hasHighErrorRate(@Input("metrics") metrics: MetricsWindow) = metrics.errorRate > 2.0
  ```
- **`@Input("binding")`**：将之前 Action 的 `outputBinding` 注入当前方法参数，避免手动查找内存。
- **`@Form`/`@UseForm`**：声明需要向用户提问的表单。Embabel 会在 Shell/MCP 中渲染问题并收集答案，再继续执行 Action，用于收集诸如“目标环境”“审批号”等字段。
- **`@RequiresConfirmation`**（示例项目中的自定义注解）：可结合 Action 拦截器要求用户输入特定确认语句，以保护危险操作。

> 以上注解组合能够覆盖“诊断→提问→修复→确认”全链路，建议在 `diagnosisAgent` 中大量使用 `@Input`、`@Form` 以保证参数完备性，在 `actionPlannerAgent` 中配合 `@Condition`、`@RequiresConfirmation` 建立安全闸门。

## 4. 编程模型
- **注解式 Agent**：
  ```kotlin
  @Agent(description = "Find news based on a person's star sign")
  class StarNewsFinder {
      @Action fun extractPerson(input: UserInput): Person?
      @Action(toolGroups = [CoreToolGroups.WEB])
      fun findNewsStories(person: StarPerson, horoscope: Horoscope): RelevantNewsStories
      @AchievesGoal("Create an amusing writeup")
      fun starNewsWriteup(...): Writeup
  }
  ```
  每个 `@Action` 会自动注册为 Tool，支持模型调用、参数校验和输出绑定。
- **DSL 式 Agent**：`agent { flow { aggregate { ... } } }` 允许以函数式方式描述复杂编排（见 `examples-kotlin/.../factchecker`）。
- **多模型策略**：可在 Action 内自由组合 OpenAI、Anthropic 等 LLM，示例中的 Researcher Agent 同时调用 GPT-4 与 Claude，并通过 critique Action 自我审查。

## 5. MCP 集成方式
- **作为 MCP Server**：运行 `scripts/kotlin/mcp_server.sh` 后，StarNewsFinder、Researcher、FactChecker 等 Agent 会暴露为 `find_horoscope_news`、`research_topic`、`check_facts` 等 Tool，供外部助手通过 JSON-RPC/SSE 调用。
- **作为 MCP Client**：Shell 启动时追加 `--docker-tools`，即可连接 Docker Desktop MCP Server，获取容器编排、镜像管理、日志采集等工具，完成 “Agent 调用外部工具” 的链路。
- **协议优势**：统一的 Schema/鉴权层保证 Tool 可插拔，有利于将本方案的日志/指标/修复能力暴露给企业内其他 Agent。

## 6. 与本 DevOps Agent 的集成思路
- **运行时选择**：在 `DevOpsAgentApplication` 中引入 `@EnableAgentShell`（交互调试）与 `@EnableAgentMcpServer`（对外暴露能力），并按环境切换 `loggingTheme` 与 `mcpServers`。
- **模块映射**：
  - `diagnosisAgent`、`actionPlannerAgent` 可直接由 Embabel Agent 定义，内部 Action 对应日志检索、指标对比、修复计划、审批、执行等 Tool。
  - Conversation Memory 可落在 Embabel `ConversationStateStore` + Redis，复用现有 Session Router。
  - 将现有 Spring Service（如 `DiagnosisService`、`ActionService`）封装为 Action Provider，保持业务逻辑复用。
- **脚手架来源**：参考 `embabel/kotlin-agent-template` 获取最小可运行骨架；利用 `embabel-agent-examples` 中的脚本、配置模式来验证本地运行、MCP 联调。

## 7. 参考资料
- Embabel Agent 源码与 Issue：https://github.com/embabel/embabel-agent
- Kotlin 模板：https://github.com/embabel/kotlin-agent-template
- 示例集合（Kotlin/Java）：https://github.com/embabel/embabel-agent-examples

该框架能够在保持 Spring Boot 编程体验的同时，提供标准化的 Agent 生命周期、Tool 管理与 MCP 互操作性，为 DevOps 日志诊断场景提供了可扩展的基础运行时。
