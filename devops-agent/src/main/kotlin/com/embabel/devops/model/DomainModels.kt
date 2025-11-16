package com.embabel.devops.model

import com.embabel.devops.config.DiagnosticsCatalogProperties
import java.time.Instant
import java.util.UUID

/** 描述一次来自会话/命令行的诊断请求。 */
data class DiagnosisRequest(
    val conversationId: String,
    val service: String,
    val environment: String,
    val windowMinutes: Int,
    val symptom: String,
)

/** 对任意日志或指标数据的统一封装。 */
data class Observation(
    val sourceId: String,
    val type: ObservationType,
    val timestamp: Instant,
    val message: String? = null,
    val measurements: Map<String, Double> = emptyMap(),
    val severity: DiagnosticsCatalogProperties.Severity = DiagnosticsCatalogProperties.Severity.INFO,
) {
    enum class ObservationType { LOGS, METRICS }
}

data class ObservationBundle(
    val logs: List<Observation>,
    val metrics: List<Observation>,
)

data class DiagnosisIssue(
    val ruleId: String,
    val summary: String,
    val severity: DiagnosticsCatalogProperties.Severity,
    val evidence: List<String>,
)

/** 诊断输出，包含命中的规则与建议动作。 */
data class DiagnosisResult(
    val id: String = UUID.randomUUID().toString(),
    val request: DiagnosisRequest,
    val issues: List<DiagnosisIssue>,
    val suggestedActions: List<String>,
)

data class DiagnosisSummary(
    val diagnosisId: String,
    val content: String,
)

/** 针对诊断结果生成的一组修复步骤。 */
data class ActionPlan(
    val planId: String = "plan-${UUID.randomUUID()}",
    val diagnosisId: String,
    val steps: List<ActionStep>,
)

data class ActionStep(
    val actionId: String,
    val description: String,
    val command: String,
    val confirmationPhrase: String,
    val dryRunSupported: Boolean,
    val rollbackCommand: String?,
)

/** 执行（或 dry-run）单个修复动作时的入参。 */
data class RemediationRequest(
    val conversationId: String,
    val requestedActionId: String,
    val dryRun: Boolean,
    val ticketId: String,
    val confirmationPhrase: String?,
)

data class RemediationDecision(
    val approved: Boolean,
    val reason: String,
)

data class ExecutionRecord(
    val executionId: String = UUID.randomUUID().toString(),
    val diagnosisId: String,
    val actionId: String,
    val status: ExecutionStatus,
    val output: String,
    val rollbackIssued: Boolean,
    val mode: ExecutionMode,
    val executedAt: Instant = Instant.now(),
) {
    enum class ExecutionStatus { COMPLETED, ROLLED_BACK }

    enum class ExecutionMode { DRY_RUN, EXECUTE }
}
