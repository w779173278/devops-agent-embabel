package com.embabel.devops.service

import com.embabel.devops.agent.DiagnosisAgent
import com.embabel.devops.config.DevOpsAgentProperties
import org.springframework.stereotype.Service

@Service
class DiagnosisWorkflow(
    private val diagnosisAgent: DiagnosisAgent,
    private val dataSourceRegistry: DataSourceRegistry,
) {
    fun run(request: DiagnosisRequest): DiagnosisWorkflowResult {
        val progress = buildMutableList(request)
        val observations = diagnosisAgent.collectSignals(request)
        progress += "已收集 ${observations.logs.size} 条日志与 ${observations.metrics.size} 条指标"
        val diagnosis = diagnosisAgent.analyze(observations, request)
        val summary = diagnosisAgent.summarize(diagnosis)
        progress += "诊断 ${diagnosis.id} 完成"
        return DiagnosisWorkflowResult(diagnosis, summary, progress)
    }

    private fun buildMutableList(request: DiagnosisRequest): MutableList<String> {
        val logs = dataSourceRegistry.primary(DevOpsAgentProperties.DataSourceType.LOGS)
        val metrics = dataSourceRegistry.primary(DevOpsAgentProperties.DataSourceType.METRICS)
        return mutableListOf(
            "正在获取 ${request.service} 在 ${request.environment} 的日志源 ${logs?.description ?: "默认日志"}",
            "正在获取 ${request.service} 在 ${request.environment} 的指标源 ${metrics?.description ?: "默认指标"}",
        )
    }
}

data class DiagnosisWorkflowResult(
    val result: DiagnosisResult,
    val summary: DiagnosisSummary,
    val progressMessages: List<String>,
)
