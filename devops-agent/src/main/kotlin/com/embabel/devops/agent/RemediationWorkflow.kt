package com.embabel.devops.agent

import com.embabel.devops.model.ExecutionRecord
import com.embabel.devops.model.RemediationRequest
import com.embabel.devops.model.DiagnosisRepository
import org.springframework.stereotype.Service

@Service
/**
 * 修复编排工作流：负责拿到诊断、生成计划、执行动作。
 */
class RemediationWorkflow(
    private val actionPlannerAgent: ActionPlannerAgent,
    private val diagnosisRepository: DiagnosisRepository,
) {
    fun execute(command: RemediationCommandRequest): ExecutionRecord {
        val diagnosis = diagnosisRepository.findById(command.diagnosisId)
            ?: error("未找到诊断 ${command.diagnosisId}")
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
