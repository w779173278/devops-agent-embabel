package com.embabel.devops.agent

import com.embabel.devops.DevOpsAgentTestConfiguration
import com.embabel.devops.service.DiagnosisRequest
import com.embabel.devops.service.DiagnosisResult
import com.embabel.devops.service.DiagnosisIssue
import com.embabel.devops.service.ExecutionRecord
import com.embabel.devops.service.RemediationRequest
import com.embabel.devops.config.DiagnosticsCatalogProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [DevOpsAgentTestConfiguration::class])
class ActionPlannerAgentTest @Autowired constructor(
    private val actionPlannerAgent: ActionPlannerAgent,
) {

    private fun sampleDiagnosis(): DiagnosisResult {
        val request = DiagnosisRequest(
            conversationId = "conv-action",
            service = "checkout",
            environment = "prod",
            windowMinutes = 30,
            symptom = "5xx",
        )
        val issue = DiagnosisIssue(
            ruleId = "checkout-error-spike",
            summary = "Spike",
            severity = DiagnosticsCatalogProperties.Severity.HIGH,
            evidence = listOf("http_error_rate=4"),
        )
        return DiagnosisResult(request = request, issues = listOf(issue), suggestedActions = listOf("restart-checkout"))
    }

    @Test
    fun `should require confirmation before execution`() {
        val diagnosis = sampleDiagnosis()
        val plan = actionPlannerAgent.proposePlan(diagnosis)
        val request = RemediationRequest(
            conversationId = "conv-action",
            requestedActionId = "restart-checkout",
            dryRun = false,
            ticketId = "INC-1",
            confirmationPhrase = null,
        )

        assertThatThrownBy {
            actionPlannerAgent.confirmPlan(plan, request)
        }.hasMessageContaining("Missing confirmation")

        val approvedRequest = request.copy(confirmationPhrase = plan.steps.first().confirmationPhrase, dryRun = true)
        val decision = actionPlannerAgent.confirmPlan(plan, approvedRequest)
        assertThat(decision.approved).isTrue

        val execution = actionPlannerAgent.executePlan(plan, decision, approvedRequest.copy(dryRun = false))
        assertThat(execution.status).isEqualTo(ExecutionRecord.ExecutionStatus.COMPLETED)
        assertThat(execution.mode).isEqualTo(ExecutionRecord.ExecutionMode.EXECUTE)
    }
}
