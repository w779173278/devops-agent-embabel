package com.embabel.devops.model

import com.embabel.devops.config.DiagnosticsCatalogProperties
import org.springframework.stereotype.Service

@Service
/**
 * 规则引擎：基于阈值 + 关键字在日志/指标中找出异常，并生成证据。
 */
class DiagnosisService(
    private val catalog: DiagnosticsCatalogProperties,
) {
    fun aggregate(bundle: ObservationBundle, request: DiagnosisRequest): DiagnosisResult {
        val issues = catalog.rules.mapNotNull { rule ->
            val metricEvidence = bundle.metrics.filter { obs ->
                obs.measurements[rule.metric]?.let { it >= rule.threshold } ?: false
            }
            val logEvidence = bundle.logs.filter { obs ->
                rule.keywords.any { keyword ->
                    obs.message?.contains(keyword, ignoreCase = true) == true
                }
            }
            if (metricEvidence.isEmpty() && logEvidence.isEmpty()) {
                null
            } else {
                DiagnosisIssue(
                    ruleId = rule.id,
                    summary = rule.description,
                    severity = rule.severity,
                    evidence = buildList {
                        metricEvidence.forEach { add("${rule.metric}=${it.measurements[rule.metric]}") }
                        logEvidence.forEach { obs -> obs.message?.let { add(it) } }
                    },
                )
            }
        }
        val suggestedActions = issues.flatMap { issue ->
            catalog.rules.firstOrNull { it.id == issue.ruleId }?.suggestedActions ?: emptyList()
        }.distinct()
        return DiagnosisResult(request = request, issues = issues, suggestedActions = suggestedActions)
    }
}
