package com.embabel.devops.service

import com.embabel.devops.agent.ActionPlannerAgent
import org.springframework.stereotype.Service

@Service
class RemediationWorkflow(
    private val actionPlannerAgent: ActionPlannerAgent,
    private val diagnosisRepository: DiagnosisRepository,
) {
    fun execute(command: RemediationCommandRequest): ExecutionRecord {
        val diagnosis = diagnosisRepository.findById(command.diagnosisId)
            ?: error("Diagnosis ${command.diagnosisId} not found")
        val plan = actionPlannerAgent.proposePlan(diagnosis)
        val remediationRequest = RemediationRequest(
            conversationId = command.conversationId,
            requestedActionId = command.actionId,
            dryRun = command.mode == RemediationCommandRequest.ExecutionMode.DRY_RUN,
            ticketId = command.ticketId,
            confirmationPhrase = command.confirmationPhrase,
        )
        val decision = actionPlannerAgent.confirmPlan(plan, remediationRequest.copy(dryRun = true))
        return actionPlannerAgent.executePlan(
            plan,
            decision,
            remediationRequest.copy(confirmationPhrase = null),
        )
    }
}

data class RemediationCommandRequest(
    val conversationId: String,
    val diagnosisId: String,
    val actionId: String,
    val ticketId: String,
    val confirmationPhrase: String?,
    val mode: ExecutionMode,
) {
    enum class ExecutionMode { DRY_RUN, EXECUTE }
}
