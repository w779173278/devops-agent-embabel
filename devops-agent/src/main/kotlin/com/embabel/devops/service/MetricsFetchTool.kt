package com.embabel.devops.service

import com.embabel.devops.config.DevOpsAgentProperties
import java.time.Instant
import kotlin.math.absoluteValue
import org.springframework.stereotype.Service

@Service
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
