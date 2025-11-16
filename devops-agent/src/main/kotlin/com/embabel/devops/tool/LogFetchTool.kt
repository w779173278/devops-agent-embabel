package com.embabel.devops.tool

import com.embabel.devops.config.DevOpsAgentProperties
import com.embabel.devops.model.DiagnosisRequest
import com.embabel.devops.model.Observation
import java.time.Instant
import org.springframework.stereotype.Service

@Service
/** 日志采集工具：演示如何注册日志数据源并回传 Observation。 */
class LogFetchTool(
    private val registry: DataSourceRegistry,
) {
    fun fetch(request: DiagnosisRequest): List<Observation> {
        val source = registry.primary(DevOpsAgentProperties.DataSourceType.LOGS)
        val description = source?.description ?: "默认日志源"
        return listOf(
            Observation(
                sourceId = source?.id ?: "logs",
                type = Observation.ObservationType.LOGS,
                timestamp = Instant.now(),
                message = "服务 ${request.service} 在 ${request.environment} 发生 ${request.symptom}，来源：$description",
                severity = com.embabel.devops.config.DiagnosticsCatalogProperties.Severity.HIGH,
            )
        )
    }
}
