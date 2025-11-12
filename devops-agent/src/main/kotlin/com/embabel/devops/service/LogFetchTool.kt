package com.embabel.devops.service

import com.embabel.devops.config.DevOpsAgentProperties
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class LogFetchTool(
    private val registry: DataSourceRegistry,
) {
    fun fetch(request: DiagnosisRequest): List<Observation> {
        val source = registry.primary(DevOpsAgentProperties.DataSourceType.LOGS)
        val description = source?.description ?: "logs"
        return listOf(
            Observation(
                sourceId = source?.id ?: "logs",
                type = Observation.ObservationType.LOGS,
                timestamp = Instant.now(),
                message = "${request.service} reported ${request.symptom} in ${request.environment} via $description",
                severity = com.embabel.devops.config.DiagnosticsCatalogProperties.Severity.HIGH,
            )
        )
    }
}
