package com.embabel.devops.service

import org.springframework.stereotype.Service

@Service
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
        val output = "${mode.name}: ${step.command} (ticket=${request.ticketId})"
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
class RollbackTool {
    fun rollback(
        diagnosisId: String,
        step: ActionStep,
        reason: String,
    ): ExecutionRecord {
        val command = step.rollbackCommand ?: "echo no rollback"
        return ExecutionRecord(
            diagnosisId = diagnosisId,
            actionId = step.actionId,
            status = ExecutionRecord.ExecutionStatus.ROLLED_BACK,
            output = "ROLLBACK: $command due to $reason",
            rollbackIssued = true,
            mode = ExecutionRecord.ExecutionMode.EXECUTE,
        )
    }
}
