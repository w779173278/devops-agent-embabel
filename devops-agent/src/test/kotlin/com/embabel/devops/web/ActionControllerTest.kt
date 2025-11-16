package com.embabel.devops.web

import com.embabel.devops.DevOpsAgentTestConfiguration
import com.embabel.devops.agent.RemediationCommandRequest
import com.embabel.devops.model.ExecutionRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(classes = [DevOpsAgentTestConfiguration::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ActionControllerTest @Autowired constructor(
    private val client: WebTestClient,
) {
    @Test
    fun `should execute remediation after confirmation`() {
        val diagnosis = client.post()
            .uri("/v1/diagnostics")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                DiagnosisRequestPayload(
                    conversationId = "conv-action-http",
                    service = "checkout",
                    environment = "prod",
                    windowMinutes = 10,
                    symptom = "error spike",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(DiagnosisResponse::class.java)
            .returnResult().responseBody!!

        val execution = client.post()
            .uri("/v1/actions/execute")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                ActionExecutionPayload(
                    conversationId = "conv-action-http",
                    diagnosisId = diagnosis.diagnosisId,
                    actionId = "restart-checkout",
                    ticketId = "INC-42",
                    confirmationPhrase = "CONFIRM_RESTART_CHECKOUT",
                    mode = RemediationCommandRequest.ExecutionMode.EXECUTE,
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(ActionExecutionResponse::class.java)
            .returnResult().responseBody!!

        assertThat(execution.status).isEqualTo(ExecutionRecord.ExecutionStatus.COMPLETED)
        assertThat(execution.mode).isEqualTo(ExecutionRecord.ExecutionMode.EXECUTE)
        assertThat(execution.diagnosisId).isEqualTo(diagnosis.diagnosisId)
    }
}
