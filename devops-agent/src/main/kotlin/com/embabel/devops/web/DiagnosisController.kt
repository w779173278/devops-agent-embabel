package com.embabel.devops.web

import com.embabel.devops.config.DiagnosticsCatalogProperties
import com.embabel.devops.service.DiagnosisRequest
import com.embabel.devops.service.DiagnosisRepository
import com.embabel.devops.service.DiagnosisWorkflow
import com.embabel.devops.service.DiagnosisWorkflowResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/diagnostics")
@Validated
class DiagnosisController(
    private val workflow: DiagnosisWorkflow,
    private val repository: DiagnosisRepository,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun runDiagnosis(@Valid @RequestBody payload: DiagnosisRequestPayload): DiagnosisResponse {
        val result = workflow.run(payload.toRequest())
        return result.toResponse()
    }

    @GetMapping("/{id}")
    fun findDiagnosis(@PathVariable id: String): DiagnosisResponse {
        val diagnosis = repository.findById(id) ?: throw DiagnosisNotFoundException(id)
        val summary = "Diagnosis ${diagnosis.id} for ${diagnosis.request.service}"
        return DiagnosisResponse(
            diagnosisId = diagnosis.id,
            service = diagnosis.request.service,
            environment = diagnosis.request.environment,
            issues = diagnosis.issues.map { it.toResponse() },
            suggestedActions = diagnosis.suggestedActions,
            summary = summary,
            progress = emptyList(),
        )
    }

    private fun DiagnosisWorkflowResult.toResponse(): DiagnosisResponse = DiagnosisResponse(
        diagnosisId = result.id,
        service = result.request.service,
        environment = result.request.environment,
        issues = result.issues.map { it.toResponse() },
        suggestedActions = result.suggestedActions,
        summary = summary.content,
        progress = progressMessages,
    )

    private fun DiagnosisRequestPayload.toRequest(): DiagnosisRequest = DiagnosisRequest(
        conversationId = conversationId,
        service = service,
        environment = environment,
        windowMinutes = windowMinutes,
        symptom = symptom,
    )
}

data class DiagnosisRequestPayload(
    @field:NotBlank
    val conversationId: String,
    @field:NotBlank
    val service: String,
    @field:NotBlank
    val environment: String,
    @field:Min(5)
    val windowMinutes: Int,
    @field:NotBlank
    val symptom: String,
)

data class DiagnosisResponse(
    val diagnosisId: String,
    val service: String,
    val environment: String,
    val issues: List<DiagnosisIssueResponse>,
    val suggestedActions: List<String>,
    val summary: String,
    val progress: List<String>,
)

data class DiagnosisIssueResponse(
    val ruleId: String,
    val severity: DiagnosticsCatalogProperties.Severity,
    val summary: String,
    val evidence: List<String>,
)

private fun com.embabel.devops.service.DiagnosisIssue.toResponse() = DiagnosisIssueResponse(
    ruleId = ruleId,
    severity = severity,
    summary = summary,
    evidence = evidence,
)

@ResponseStatus(HttpStatus.NOT_FOUND)
class DiagnosisNotFoundException(id: String) : RuntimeException("Diagnosis $id not found")
