/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.example.researcher

import com.embabel.agent.api.annotation.*
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.create
import com.embabel.agent.api.models.AnthropicModels
import com.embabel.agent.api.models.OpenAiModels
import com.embabel.agent.core.CoreToolGroups
import com.embabel.agent.domain.io.UserInput
import com.embabel.agent.domain.library.ResearchReport
import com.embabel.agent.prompt.PromptUtils
import com.embabel.agent.prompt.ResponseFormat
import com.embabel.agent.prompt.persona.Persona
import com.embabel.common.ai.model.LlmOptions
import com.embabel.common.ai.model.ModelProvider.Companion.CHEAPEST_ROLE
import com.embabel.common.ai.prompt.PromptContributor
import com.embabel.common.ai.prompt.PromptContributorConsumer
import com.embabel.common.core.types.Timestamped
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

data class SingleLlmReport(
    val report: ResearchReport,
    val model: String,
) : Timestamped {
    override val timestamp: Instant = Instant.now()
}

data class Critique(
    val accepted: Boolean,
    val reasoning: String,
)


@ConfigurationProperties(prefix = "embabel.examples.researcher")
class ResearcherProperties(
    val responseFormat: ResponseFormat = ResponseFormat.MARKDOWN,
    val maxWordCount: Int = 300,
    val claudeModelName: String = AnthropicModels.CLAUDE_35_HAIKU,
    val openAiModelName: String = OpenAiModels.GPT_41_MINI,
    val criticModeName: String = OpenAiModels.GPT_41,
    val mergeModelName: String = OpenAiModels.GPT_41_MINI,
    personaName: String = "Sherlock",
    personaDescription: String = "A resourceful researcher agent that can perform deep web research on a topic. Nothing escapes Sherlock",
    personaVoice: String = "Your voice is dry and in the style of Sherlock Holmes. Occasionally you address the user as Watson",
    personaObjective: String = "To clarify all points the user has brought up",
) : PromptContributorConsumer {
    // 直接创建 Persona 实例而非通过继承
    val persona = Persona(personaName, personaDescription, personaVoice, personaObjective)

    override val promptContributors: List<PromptContributor>
        get() = listOf(
            responseFormat,
            persona,
        )
}

enum class Category {
    QUESTION,
    DISCUSSION,
}

data class Categorization(
    val category: Category,
)

/**
 * Researcher Agent 基于 Embabel 模型执行自主调研。
 *
 * 核心特性：
 * 1. 多模型协作：并用 GPT-4 与 Claude 获取资料
 * 2. 自我批判：对报告评分并按需重跑
 * 3. 并行执行：多个调研动作同时运行
 * 4. 条件控制：依赖“满意/不满意”条件推进流程
 * 5. 模型融合：整合不同 LLM 的结果获得更优输出
 *
 * 流程概述：
 * - 先将输入分类为问题或讨论
 * - 并行调用多种 LLM 开展调研
 * - 合并不同模型的调研报告
 * - 自我审查合并后的报告
 * - 若不满意，针对特定模型重新调研
 * - 最终在报告满意后交付结果
 */
@Agent(
    description = "Perform deep web research on a topic",
)
class Researcher(
    val properties: ResearcherProperties,
) {

    private val logger = LoggerFactory.getLogger(Researcher::class.java)

    init {
        logger.info("Researcher agent initialized: $properties")
    }

    /**
     * 为用户输入做分类以决定调研策略。
     * 使用最便宜的 LLM 高效完成分类任务。
     *
     * @param userInput 用户的提问或话题
     * @return 将输入标记为 QUESTION 或 DISCUSSION
     */
    @Action
    fun categorize(
        userInput: UserInput,
        context: OperationContext,
    ): Categorization = context.ai()
        .withLlmByRole(CHEAPEST_ROLE)
        .create(
            """
        Categorize the following user input:

        Topic:
        <${userInput.content}>
    """.trimIndent()
        )

    /**
     * 使用 GPT-4 模型执行调研，是两条并行路径之一（另一条是 Claude）。
     *
     * @param userInput 用户的提问或话题
     * @param categorization 输入分类
     * @param context 操作上下文，提供工具与服务
     * @return GPT-4 模型生成的调研报告
     */
    // 需要不同的 output binding，否则只会运行其中一个 Action
    @Action(
        post = [REPORT_SATISFACTORY],
        canRerun = true,
        outputBinding = "gpt4Report",
        toolGroups = [CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION]
    )
    fun researchWithGpt4(
        userInput: UserInput,
        categorization: Categorization,
        context: OperationContext,
    ): SingleLlmReport = researchWith(
        userInput = userInput,
        categorization = categorization,
        critique = null,
        llm = LlmOptions(properties.openAiModelName),
        context = context,
    )

    /**
     * 在收到“不满意”评价后，使用 GPT-4 重做调研，展示 Agent 的自我改进能力。
     *
     * @param userInput 用户提问或话题
     * @param categorization 输入分类
     * @param critique 之前报告的差评及原因
     * @param context 操作上下文，提供工具与服务
     * @return 改进后的 GPT-4 调研报告
     */
    @Action(
        pre = [REPORT_UNSATISFACTORY],
        post = [REPORT_SATISFACTORY],
        canRerun = true,
        outputBinding = "gpt4Report",
        toolGroups = [CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION]
    )
    fun redoResearchWithGpt4(
        userInput: UserInput,
        categorization: Categorization,
        critique: Critique,
        context: OperationContext,
    ): SingleLlmReport = researchWith(
        userInput = userInput,
        categorization = categorization,
        critique = critique,
        llm = LlmOptions(properties.openAiModelName),
        context = context,
    )

    /**
     * 使用 Claude 模型执行调研，与 GPT-4 路径并行。
     *
     * @param userInput 用户提问或话题
     * @param categorization 输入分类
     * @param context 操作上下文
     * @return Claude 模型生成的调研报告
     */
    @Action(
        post = [REPORT_SATISFACTORY],
        outputBinding = "claudeReport",
        canRerun = true,
        toolGroups = [CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION]
    )
    fun researchWithClaude(
        userInput: UserInput,
        categorization: Categorization,
        context: OperationContext,
    ): SingleLlmReport = researchWith(
        userInput = userInput,
        categorization = categorization,
        critique = null,
        llm = LlmOptions(properties.claudeModelName),
        context = context,
    )

    /**
     * 在收到“不满意”评价后，使用 Claude 重新调研，体现基于反馈迭代的能力。
     *
     * @param userInput 用户提问或话题
     * @param categorization 输入分类
     * @param critique 上一次报告被退回的原因
     * @param context 操作上下文
     * @return 改进后的 Claude 调研报告
     */
    @Action(
        pre = [REPORT_UNSATISFACTORY],
        post = [REPORT_SATISFACTORY],
        outputBinding = "claudeReport",
        canRerun = true,
        toolGroups = [CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION]
    )
    fun redoResearchWithClaude(
        userInput: UserInput,
        categorization: Categorization,
        critique: Critique,
        context: OperationContext,
    ): SingleLlmReport = researchWith(
        userInput = userInput,
        categorization = categorization,
        critique = critique,
        llm = LlmOptions(properties.claudeModelName),
        context = context,
    )

    /**
     * 不同模型共享的调研实现，会根据分类路由到对应方法。
     *
     * @param userInput 用户提问或话题
     * @param categorization 输入分类
     * @param critique 可选的前次点评
     * @param llm 目标 LLM 配置
     * @param context 操作上下文
     * @return 对应模型产出的调研报告
     */
    private fun researchWith(
        userInput: UserInput,
        categorization: Categorization,
        critique: Critique?,
        llm: LlmOptions,
        context: OperationContext,
    ): SingleLlmReport {
        val researchReport = when (
            categorization.category
        ) {
            Category.QUESTION -> answerQuestion(userInput, llm, critique, context)
            Category.DISCUSSION -> research(userInput, llm, critique, context)
        }
        return SingleLlmReport(
            report = researchReport,
            model = llm.criteria.toString(),
        )
    }

    /**
     * 针对具体问题生成调研报告，利用 Web 工具找到具备引用的精准答案。
     *
     * @param userInput 用户问题
     * @param llm LLM 选型
     * @param critique 可选的前次点评
     * @param context 操作上下文
     * @return 回答该问题的研究报告
     */
    private fun answerQuestion(
        userInput: UserInput,
        llm: LlmOptions,
        critique: Critique?,
        context: OperationContext,
    ): ResearchReport = context.promptRunner(
        llm = llm,
        promptContributors = properties.promptContributors,
    ).create(
        """
        Use the web and browser tools to answer the given question.

        You must try to find the answer on the web, and be definite, not vague.

        Write a detailed report in at most ${properties.maxWordCount} words.
        If you can answer the question more briefly, do so.
        Including a number of links that are relevant to the topic.

        Example:
        ${PromptUtils.jsonExampleOf<ResearchReport>()}

        Question:
        <${userInput.content}>

        ${
            critique?.reasoning?.let {
                "Critique of previous answer:\n<$it>"
            }
        }
    """.trimIndent()
    )

    /**
     * 针对讨论类话题生成调研报告，利用 Web 工具汇集信息并提供综述。
     *
     * @param userInput 用户指定的研究话题
     * @param llm LLM 选型
     * @param critique 可选的前次点评
     * @param context 操作上下文
     * @return 关于该话题的研究报告
     */
    private fun research(
        userInput: UserInput,
        llm: LlmOptions,
        critique: Critique?,
        context: OperationContext,
    ): ResearchReport = context.promptRunner(
        llm = llm,
        promptContributors = properties.promptContributors,
    ).create(
        """
        Use the web and browser tools to perform deep research on the given topic.

        Write a detailed report in ${properties.maxWordCount} words,
        including a number of links that are relevant to the topic.

        Topic:
        <${userInput.content}>

         ${
            critique?.reasoning?.let {
                "Critique of previous answer:\n<$it>"
            }
        }
    """.trimIndent()
    )

    /**
     * 评估合并后的调研报告质量，实现 Embabel 模型的自我批判能力。
     *
     * @param userInput 用户的原始提问或话题
     * @param mergedReport 待评估的合并报告
     * @return 包含接受状态与理由的评语
     */
    @Action(post = [REPORT_SATISFACTORY], canRerun = true)
    fun critiqueMergedReport(
        userInput: UserInput,
        @RequireNameMatch mergedReport: ResearchReport,
        context: OperationContext,
    ): Critique = context.ai().withLlm(properties.criticModeName)
        .create(
            """
            Is this research report satisfactory? Consider the following question:
            <${userInput.content}>
            The report is satisfactory if it answers the question with adequate references.
            It is possible that the question does not have a clear answer, in which
            case the report is satisfactory if it provides a reasonable discussion of the topic.

            ${mergedReport.infoString(verbose = true)}
        """.trimIndent(),
        )

    /**
     * 将不同模型的调研报告合并为更优的一份，体现多模型协作能力。
     *
     * @param userInput 用户原始提问或话题
     * @param gpt4Report GPT-4 产出的报告
     * @param claudeReport Claude 产出的报告
     * @return 融合两者优点的合并报告
     */
    @Action(
        post = [REPORT_SATISFACTORY],
        outputBinding = "mergedReport",
        canRerun = true,
    )
    fun mergeReports(
        userInput: UserInput,
        @RequireNameMatch gpt4Report: SingleLlmReport,
        @RequireNameMatch claudeReport: SingleLlmReport,
        context: OperationContext,
    ): ResearchReport {
        val reports = listOf(
            gpt4Report,
            claudeReport,
        )
        return context.promptRunner(
            llm = LlmOptions(properties.criticModeName),
            promptContributors = properties.promptContributors,
        ).create(
            """
        Merge the following research reports into a single report taking the best of each.
        Consider the user direction: <${userInput.content}>

        ${reports.joinToString("\n\n") { "Report from ${it.model}\n${it.report.infoString(verbose = true)}" }}
    """.trimIndent()
        )
    }

    /**
     * 判断报告是否“满意”的条件，用于推进工作流。
     *
     * @param critique 报告对应的评价
     * @return 满意则返回 true
     */
    @Condition(name = REPORT_SATISFACTORY)
    fun makesTheGrade(
        critique: Critique,
    ): Boolean = critique.accepted

    /**
     * 判断报告是否“不满意”的条件，用于触发调研返工。
     *
     * @param critique 报告对应的评价
     * @return 不满意则返回 true
     */
    // TODO 理论上这里应该能直接取反
    @Condition(name = REPORT_UNSATISFACTORY)
    fun rejected(
        critique: Critique,
    ): Boolean = !critique.accepted

    /**
     * 最终动作：接受调研报告并作为 Agent 输出，标志任务完成。
     *
     * @param mergedReport 最终合并的研究报告
     * @param critique 确认满意的评语
     * @return 最终调研报告
     */
    @AchievesGoal(
        description = "Completes a research or question answering task, producing a research report",
    )
    // TODO 若 output binding 未绑定到新对象则无法完成
    // 虽然说得通，但依然有点出乎意料
    @Action(pre = [REPORT_SATISFACTORY], outputBinding = "finalResearchReport")
    fun acceptReport(
        @RequireNameMatch mergedReport: ResearchReport,
        critique: Critique,
    ) = mergedReport

    companion object {
        /** 表示“报告满意”状态的条件常量 */
        const val REPORT_SATISFACTORY = "reportSatisfactory"

        /** 表示“报告不满意”状态的条件常量 */
        const val REPORT_UNSATISFACTORY = "reportUnsatisfactory"
    }
}
