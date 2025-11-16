package com.embabel.devops.tool

import com.embabel.devops.model.ActionStep
import com.embabel.devops.model.ExecutionRecord
import com.embabel.devops.model.RemediationRequest
import org.springframework.stereotype.Service

@Service
/**
 * 包装底层命令执行，统一处理确认口令、dry-run 与执行记录。
 */
class ActionExecutorTool(
    private val confirmationService: RemediationConfirmationService,
) {
    fun execute(
        diagnosisId: String,
        step: ActionStep,
        request: RemediationRequest,
    ): ExecutionRecord {
        confirmationService.requireConfirmation(request.conversationId, request.confirmationPhrase, step.confirmationPhrase)
        val dryRun = request.dryRun && step.dryRunSupported
        val mode = if (dryRun) ExecutionRecord.ExecutionMode.DRY_RUN else ExecutionRecord.ExecutionMode.EXECUTE
        val output = "${mode.name}: ${step.command} (工单=${request.ticketId})"
        return ExecutionRecord(
            diagnosisId = diagnosisId,
            actionId = step.actionId,
            status = ExecutionRecord.ExecutionStatus.COMPLETED,
            output = output,
            rollbackIssued = false,
            mode = mode,
        )
    }
}

@Service
/** 简化版回滚工具，便于在 demo 中模拟回滚输出。 */
class RollbackTool {
    fun rollback(
        diagnosisId: String,
        step: ActionStep,
        reason: String,
    ): ExecutionRecord {
        val command = step.rollbackCommand ?: "echo 'no rollback available'"
        return ExecutionRecord(
            diagnosisId = diagnosisId,
            actionId = step.actionId,
            status = ExecutionRecord.ExecutionStatus.ROLLED_BACK,
            output = "回滚执行：$command，触发原因：$reason",
            rollbackIssued = true,
            mode = ExecutionRecord.ExecutionMode.EXECUTE,
        )
    }
}
