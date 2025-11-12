package com.embabel.devops.config

import org.springframework.boot.context.properties.ConfigurationProperties
@ConfigurationProperties(prefix = "diagnostics")
data class DiagnosticsCatalogProperties(
    val rules: List<DiagnosisRule> = emptyList(),
) {
    data class DiagnosisRule(
        val id: String,
        val description: String,
        val severity: Severity,
        val metric: String,
        val threshold: Double,
        val keywords: List<String> = emptyList(),
        val suggestedActions: List<String> = emptyList(),
    )

    enum class Severity {
        INFO,
        MEDIUM,
        HIGH,
        CRITICAL,
    }
}
