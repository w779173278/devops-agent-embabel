package com.embabel.devops.web

import com.embabel.devops.service.ExecutionRecord
import com.embabel.devops.service.RemediationCommandRequest
import com.embabel.devops.service.RemediationWorkflow
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/actions")
@Validated
class ActionController(
    private val remediationWorkflow: RemediationWorkflow,
) {
    @PostMapping("/execute")
    @ResponseStatus(HttpStatus.OK)
    fun execute(@Valid @RequestBody payload: ActionExecutionPayload): ActionExecutionResponse {
        val record = remediationWorkflow.execute(payload.toCommand())
        return record.toResponse()
    }

    private fun ActionExecutionPayload.toCommand(): RemediationCommandRequest = RemediationCommandRequest(
        conversationId = conversationId,
        diagnosisId = diagnosisId,
        actionId = actionId,
        ticketId = ticketId,
        confirmationPhrase = confirmationPhrase,
        mode = mode,
    )

    private fun ExecutionRecord.toResponse(): ActionExecutionResponse = ActionExecutionResponse(
        executionId = executionId,
        diagnosisId = diagnosisId,
        actionId = actionId,
        output = output,
        status = status,
        mode = mode,
        rollbackIssued = rollbackIssued,
        executedAt = executedAt.toString(),
    )
}

data class ActionExecutionPayload(
    @field:NotBlank
    val conversationId: String,
    @field:NotBlank
    val diagnosisId: String,
    @field:NotBlank
    val actionId: String,
    @field:NotBlank
    val ticketId: String,
    val confirmationPhrase: String?,
    val mode: RemediationCommandRequest.ExecutionMode = RemediationCommandRequest.ExecutionMode.DRY_RUN,
)

data class ActionExecutionResponse(
    val executionId: String,
    val diagnosisId: String,
    val actionId: String,
    val output: String,
    val status: ExecutionRecord.ExecutionStatus,
    val mode: ExecutionRecord.ExecutionMode,
    val rollbackIssued: Boolean,
    val executedAt: String,
)
