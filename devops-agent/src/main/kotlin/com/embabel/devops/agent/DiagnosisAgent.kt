package com.embabel.devops.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Input
import com.embabel.devops.service.ConversationMemoryService
import com.embabel.devops.service.DiagnosisRequest
import com.embabel.devops.service.DiagnosisRepository
import com.embabel.devops.service.DiagnosisResult
import com.embabel.devops.service.DiagnosisService
import com.embabel.devops.service.DiagnosisSummary
import com.embabel.devops.service.LogFetchTool
import com.embabel.devops.service.MetricsFetchTool
import com.embabel.devops.service.ObservationBundle
import org.springframework.stereotype.Component

@Agent(
    name = "diagnosisAgent",
    description = "Diagnose DevOps incidents across logs and metrics",
)
@Component
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
        @Input("observations") bundle: ObservationBundle,
        request: DiagnosisRequest,
    ): DiagnosisResult {
        val result = diagnosisService.aggregate(bundle, request)
        memoryService.rememberDiagnosis(result)
        return diagnosisRepository.save(result)
    }

    @AchievesGoal("Produce a summarized diagnosis")
    @Action
    fun summarize(@Input("diagnosis") result: DiagnosisResult): DiagnosisSummary {
        val content = buildString {
            appendLine("Diagnosis ${result.id} for ${result.request.service}")
            result.issues.forEach { issue ->
                appendLine("- ${issue.ruleId}: ${issue.summary}")
            }
            if (result.suggestedActions.isNotEmpty()) {
                appendLine("Suggested actions: ${result.suggestedActions.joinToString()}")
            }
        }
        return DiagnosisSummary(result.id, content.trim())
    }
}
