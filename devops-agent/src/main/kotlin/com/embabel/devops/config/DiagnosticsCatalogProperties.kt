package com.embabel.devops.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 诊断规则目录（来源 `diagnostics.yml`），用于驱动规则匹配与建议输出。
 */
@ConfigurationProperties(prefix = "diagnostics")
data class DiagnosticsCatalogProperties(
    val rules: List<DiagnosisRule> = emptyList(),
) {
    /**
     * 单个规则定义，描述触发条件与推荐的修复动作。
     */
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
