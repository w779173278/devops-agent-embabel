package com.embabel.devops.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.devops.model.ActionPlan
import com.embabel.devops.model.ActionStep
import com.embabel.devops.model.DiagnosisResult
import com.embabel.devops.model.ExecutionRecord
import com.embabel.devops.model.ExecutionRecordRepository
import com.embabel.devops.model.RemediationDecision
import com.embabel.devops.model.RemediationRequest
import com.embabel.devops.tool.ActionExecutorTool
import com.embabel.devops.tool.RemediationPlannerTool
import com.embabel.devops.tool.RollbackTool
import org.springframework.stereotype.Component

@Agent(
    name = "actionPlannerAgent",
    description = "规划并执行修复流程的多工具 Agent",
)
@Component
/**
 * 行动规划 Agent：根据诊断结果生成修复计划、请求人工确认并执行命令。
 */
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
    /**
     * 通过执行一次 dry-run 来验证指定的修复步骤，确保人类确认后再执行。
     */
    fun confirmPlan(
        plan: ActionPlan,
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
            reason = "干跑 ${preview.executionId} 已完成，目标动作 ${step.actionId}",
        )
    }

    @AchievesGoal(description = "执行已确认的修复计划")
    @Action
    fun executePlan(
       plan: ActionPlan,
       decision: RemediationDecision,
        request: RemediationRequest,
    ): ExecutionRecord {
        require(decision.approved) { "修复请求尚未获批" }
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
        val step = plan.steps.firstOrNull() ?: error("计划 ${plan.planId} 中没有可回滚的步骤")
        return rollbackTool.rollback(plan.diagnosisId, step, reason)
    }

    private fun findRequestedStep(plan: ActionPlan, actionId: String): ActionStep =
        plan.steps.firstOrNull { it.actionId == actionId }
            ?: error("在计划 ${plan.planId} 中找不到动作 $actionId")
}
