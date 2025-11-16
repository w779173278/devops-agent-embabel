package com.embabel.devops.tool

import com.embabel.devops.config.DevOpsAgentProperties
import com.embabel.devops.model.DiagnosisRequest
import com.embabel.devops.model.Observation
import java.time.Instant
import kotlin.math.absoluteValue
import org.springframework.stereotype.Service

@Service
/** 指标采集工具：构造指标观测值供规则匹配。 */
class MetricsFetchTool(
    private val registry: DataSourceRegistry,
) {
    fun fetch(request: DiagnosisRequest): List<Observation> {
        val source = registry.primary(DevOpsAgentProperties.DataSourceType.METRICS)
        val ratio = (request.symptom.length % 5 + 2).toDouble()
        return listOf(
            Observation(
                sourceId = source?.id ?: "metrics",
                type = Observation.ObservationType.METRICS,
                timestamp = Instant.now(),
                measurements = mapOf(
                    "http_error_rate" to ratio,
                    "latency_p95" to request.windowMinutes.toDouble().absoluteValue,
                ),
                severity = com.embabel.devops.config.DiagnosticsCatalogProperties.Severity.MEDIUM,
            )
        )
    }
}
