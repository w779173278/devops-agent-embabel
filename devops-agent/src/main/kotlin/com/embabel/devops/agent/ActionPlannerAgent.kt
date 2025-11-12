package com.embabel.devops.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Input
import com.embabel.devops.service.ActionExecutorTool
import com.embabel.devops.service.ActionPlan
import com.embabel.devops.service.ActionStep
import com.embabel.devops.service.ConversationMemoryService
import com.embabel.devops.service.DiagnosisResult
import com.embabel.devops.service.ExecutionRecord
import com.embabel.devops.service.ExecutionRecordRepository
import com.embabel.devops.service.RemediationDecision
import com.embabel.devops.service.RemediationPlannerTool
import com.embabel.devops.service.RemediationRequest
import com.embabel.devops.service.RollbackTool
import org.springframework.stereotype.Component

@Agent(
    name = "actionPlannerAgent",
    description = "Plan and execute remediation workflows",
)
@Component
class ActionPlannerAgent(
    private val plannerTool: RemediationPlannerTool,
    private val executorTool: ActionExecutorTool,
    private val rollbackTool: RollbackTool,
    private val memoryService: ConversationMemoryService,
    private val executionRecordRepository: ExecutionRecordRepository,
) {
    @Action(outputBinding = "remediationPlan")
    fun proposePlan(diagnosisResult: DiagnosisResult): ActionPlan = plannerTool.plan(diagnosisResult)

    @Action(outputBinding = "remediationDecision")
    fun confirmPlan(
        @Input("remediationPlan") plan: ActionPlan,
        request: RemediationRequest,
    ): RemediationDecision {
        val step = findRequestedStep(plan, request.requestedActionId)
        val preview = executorTool.execute(
            plan.diagnosisId,
            step,
            request.copy(dryRun = true, confirmationPhrase = request.confirmationPhrase),
        )
        return RemediationDecision(
            approved = true,
            reason = "Dry run ${preview.executionId} complete for ${step.actionId}",
        )
    }

    @AchievesGoal("Execute the approved remediation plan")
    @Action
    fun executePlan(
        @Input("remediationPlan") plan: ActionPlan,
        @Input("remediationDecision") decision: RemediationDecision,
        request: RemediationRequest,
    ): ExecutionRecord {
        require(decision.approved) { "Remediation request not approved" }
        val step = findRequestedStep(plan, request.requestedActionId)
        val record = executorTool.execute(plan.diagnosisId, step, request)
        executionRecordRepository.save(record)
        memoryService.rememberAction(request.conversationId, step.actionId)
        return record
    }

    fun rollback(
        plan: ActionPlan,
        reason: String,
    ): ExecutionRecord {
        val step = plan.steps.firstOrNull() ?: error("No steps to rollback")
        return rollbackTool.rollback(plan.diagnosisId, step, reason)
    }

    private fun findRequestedStep(plan: ActionPlan, actionId: String): ActionStep =
        plan.steps.firstOrNull { it.actionId == actionId }
            ?: error("Action $actionId not found in plan ${plan.planId}")
}
