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
package com.embabel.example.horoscope

import com.embabel.agent.api.annotation.*
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.createObject
import com.embabel.agent.api.common.createObjectIfPossible
import com.embabel.agent.api.models.OpenAiModels
import com.embabel.agent.core.CoreToolGroups
import com.embabel.agent.domain.io.UserInput
import com.embabel.agent.domain.library.HasContent
import com.embabel.agent.domain.library.Person
import com.embabel.agent.domain.library.RelevantNewsStories
import com.embabel.common.ai.model.LlmOptions
import com.embabel.ux.form.Text
import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.springframework.beans.factory.annotation.Value

/**
 * 表示某人星座信息的数据类。
 * 通过表单交互采集星座字段。
 */
@JsonClassDescription("Astrological details for a person")
data class Starry(
    @Text(label = "Star sign")
    val sign: String,
)

/**
 * 表示同时包含基础资料与星座信息的人员数据类。
 * 实现 Person 接口，以便在 Agent 框架中复用与人物相关的能力。
 */
@JsonClassDescription("Person with astrology details")
@JsonDeserialize(`as` = StarPerson::class)
data class StarPerson(
    override val name: String,
    @get:JsonPropertyDescription("Star sign")
    val sign: String,
) : Person

/**
 * 保存某人星座运势摘要的数据类。
 * 作为 HoroscopeService 返回文本的包装器。
 */
data class Horoscope(
    val summary: String,
)

/**
 * 表示 Agent 工作流最终输出的数据类。
 * 实现 HasContent 接口，方便以统一方式读取文本内容。
 */
data class Writeup(
    override val content: String,
) : HasContent

/**
 * 基于用户星座找到定制新闻的 Agent。
 *
 * 工作流示例：
 * 1. 从用户输入中抽取人物信息
 * 2. 收集星座详情
 * 3. 获取当日运势
 * 4. 按运势查找相关新闻
 * 5. 产出融合运势与新闻的个性化文案
 *
 * Agent 依赖 Spring 的依赖注入提供服务，并通过 @Agent/@Action 等注解式编程
 * 描述自身能力与流程。
 */
@Agent(
    description = "Find news based on a person's star sign",
    scan = true,
    beanName = "KotlinStarNewsFinder",
)
class StarNewsFinder(
    private val horoscopeService: HoroscopeService,
    @param:Value("\${star-news-finder.model:gpt-4.1-mini}")
//    @Value("\${star-news-finder.model:ai/llama3.2}")

    private val model: String = OpenAiModels.GPT_41_NANO,
    @param:Value("\${star-news-finder.story.count:5}")
    private val storyCount: Int,
    @param:Value("\${star-news-finder.word.count:100}")
    private val wordCount: Int,
) {

    /**
     * 通过解析文本从用户输入中抽取人物实体。
     *
     * 使用轻量 LLM（GPT-41-NANO）高效提取姓名，适合作为仅提供基础信息时的流程入口。
     *
     * @param userInput 用户的文本输入
     * @return 抽取成功则返回 Person，否则为 null
     */
    @Action
    fun extractPerson(userInput: UserInput, context: OperationContext): Person? =
        // 所有提示词都是强类型的
        context.ai().withDefaultLlm().createObjectIfPossible(
            """
            从此用户输入创建一个人员，提取其姓名：
            ${userInput.content}
            """.trimIndent()
        )

    /**
     * 通过表单向用户收集星座信息。
     *
     * 该方法被标记为高成本（100.0），提示规划过程中除非别无他法才调用，
     * 以避免频繁向用户再次提问。
     *
     * @param person 需要补充星座信息的人员
     * @return 包含星座的 Starry 对象
     */
    @Action(cost = 100.0) // 成本很高，除非没有其他路径，否则规划器不会选它
    internal fun makeStarry(
        person: Person,
    ): Starry =
        fromForm("让我们来了解一些占星学细节 ${person.name}")

    /**
     * 将人员基础信息与星座详情组合成 StarPerson。
     *
     * 这是工作流中的数据转换步骤，用于生成包含星座的专用人物对象。
     *
     * @param person 基础人员信息
     * @param starry 星座详情
     * @return 组合后的 StarPerson
     */
    @Action
    fun assembleStarPerson(
        person: Person,
        starry: Starry,
    ): StarPerson {
        return StarPerson(
            name = person.name,
            sign = starry.sign,
        )
    }

    /**
     * 直接从用户输入中抽取姓名与星座。
     *
     * 当文本中已包含相关信息时，可作为替代入口一步获得完整 StarPerson。
     *
     * @param userInput 用户的文本输入
     * @return 抽取成功则返回 StarPerson，否则为 null
     */
    @Action
    fun extractStarPerson(userInput: UserInput, context: OperationContext): StarPerson? =
        context.ai().withAutoLlm().createObjectIfPossible(
            """
            从此用户输入创建人员，提取其姓名和星座:
            ${userInput.content}
            """.trimIndent()
        )

    /**
     * 根据星座获取当日运势。
     *
     * 调用注入的 HoroscopeService 拉取原始文本，并封装为 Horoscope 供后续步骤使用。
     *
     * @param starPerson 含星座信息的人员
     * @return 携带运势文本的 Horoscope
     */
    @Action
    fun retrieveHoroscope(starPerson: StarPerson) =
        Horoscope(horoscopeService.dailyHoroscope(starPerson.sign))

    /**
     * 使用网页工具，基于运势查找相关新闻。
     *
     * 该方法要求具备 toolGroups 中声明的工具，以便搜索、概括与运势主题相关的新闻。
     * LLM 会解读运势、生成搜索词并总结结果。
     *
     * @param person 含星座的人员
     * @param horoscope 当日运势
     * @return 带摘要与链接的相关新闻集合
     */
    // toolGroups 指出运行此 Action 所需的工具
    @Action(toolGroups = [CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION])
    internal fun findNewsStories(
        person: StarPerson,
        horoscope: Horoscope,
        context: OperationContext
    ): RelevantNewsStories =
        context.ai().withLlm(model).createObject(
            """
            ${person.name} 是一位占星术信徒，星座为 ${person.sign}。
            他们今天的星座运势是：
                <horoscope>${horoscope.summary}</horoscope>
            鉴于此，请使用 Web 工具并生成搜索查询
            要找到${storyCount}相关的新闻报道，请用几句话总结它们。
            包括每个故事的 URL。
            不要寻找另一个星座运势读数或直接返回有关占星术的结果;
            查找与上述阅读相关的故事。

例如：
            - 如果星座运势说他们可能
            想要处理人际关系，你可以找到关于
            新奇礼物
            - 如果星座运势说他们可能想在自己的职业生涯上工作，
            查找有关培训课程的新闻报道。
            """.trimIndent()
        )

    /**
     * 生成结合运势与相关新闻的个性化文案。
     *
     * 这是工作流的最后一步，并通过 @AchievesGoal 表明完成该 Action 即达成目标。
     * 此处将 LLM 温度提升至 0.9，以更具创意、趣味的方式融合运势和新闻。
     *
     * @param person 含星座的人员
     * @param relevantNewsStories 查到的相关新闻集合
     * @param horoscope 当日运势
     * @return 包含最终 Markdown 文案的 Writeup
     */
    // @AchievesGoal 表示完成该 Action 即可满足目标，Agent 流程可以结束
    @AchievesGoal(
        description = "根据目标人的星座为他们写一篇有趣的文章",
        export = Export(
            remote = true,
            name = "StarNewsWriteup",
            startingInputTypes = [StarPerson::class],
        )
    )
    @Action
    fun starNewsWriteup(
        person: StarPerson,
        relevantNewsStories: RelevantNewsStories,
        horoscope: Horoscope,
        context: OperationContext
    ): Writeup =
        context.ai().withLlm(
            llm = LlmOptions.withModel(model).withTemperature(0.9)
        ).createObject(
            """
        Take the following news stories and write up something
        amusing for the target person in $wordCount words.

        Begin by summarizing their horoscope in a concise, amusing way, then
        talk about the news. End with a surprising signoff.

        ${person.name} is an astrology believer with the sign ${person.sign}.
        Their horoscope for today is:
            <horoscope>${horoscope.summary}</horoscope>
        Relevant news stories are:
        ${relevantNewsStories.items.joinToString("\n") { "- ${it.url}: ${it.summary}" }}

        Format it as Markdown with links.
        """.trimIndent()
        )

}
