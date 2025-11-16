package com.embabel.devops.agent


import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.devops.model.DiagnosisRepository
import com.embabel.devops.model.DiagnosisRequest
import com.embabel.devops.model.DiagnosisResult
import com.embabel.devops.model.DiagnosisService
import com.embabel.devops.model.DiagnosisSummary
import com.embabel.devops.model.ObservationBundle
import com.embabel.devops.tool.LogFetchTool
import com.embabel.devops.tool.MetricsFetchTool
import org.springframework.stereotype.Component

@Agent(
    name = "diagnosisAgent",
    description = "聚合日志与指标以输出 DevOps 诊断结果",
)
@Component
/**
 * 诊断 Agent：负责采集观测信号、生成诊断结论，并输出可读摘要。
 */
class DiagnosisAgent(
    private val logFetchTool: LogFetchTool,
    private val metricsFetchTool: MetricsFetchTool,
    private val diagnosisService: DiagnosisService,
    private val memoryService: ConversationMemoryService,
    private val diagnosisRepository: DiagnosisRepository,
) {
    @Action(outputBinding = "observations")
    fun collectSignals(request: DiagnosisRequest): ObservationBundle {
        val logs = logFetchTool.fetch(request)
        val metrics = metricsFetchTool.fetch(request)
        return ObservationBundle(logs = logs, metrics = metrics)
    }

    @Action(outputBinding = "diagnosis")
    fun analyze(
        bundle: ObservationBundle,
        request: DiagnosisRequest,
    ): DiagnosisResult {
        val result = diagnosisService.aggregate(bundle, request)
        memoryService.rememberDiagnosis(result)
        return diagnosisRepository.save(result)
    }

    @AchievesGoal(description = "生成结构化诊断摘要")
    @Action
    fun summarize(result: DiagnosisResult): DiagnosisSummary {
        val content = buildString {
            appendLine("诊断 ${result.id}（服务：${result.request.service}）")
            result.issues.forEach { issue ->
                appendLine("- 规则 ${issue.ruleId}：${issue.summary}")
            }
            if (result.suggestedActions.isNotEmpty()) {
                appendLine("建议执行的动作：${result.suggestedActions.joinToString()}")
            }
        }
        return DiagnosisSummary(result.id, content.trim())
    }
}
