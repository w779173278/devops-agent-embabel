package com.embabel.devops.web

import com.embabel.devops.DevOpsAgentTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(classes = [DevOpsAgentTestConfiguration::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class DiagnosisControllerTest @Autowired constructor(
    private val client: WebTestClient,
) {
    @Test
    fun `should create and fetch diagnosis`() {
        val payload = DiagnosisRequestPayload(
            conversationId = "conv-http",
            service = "checkout",
            environment = "prod",
            windowMinutes = 15,
            symptom = "500 errors",
        )

        val diagnosisId = client.post()
            .uri("/v1/diagnostics")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .exchange()
            .expectStatus().isOk
            .expectBody(DiagnosisResponse::class.java)
            .returnResult()
            .responseBody!!.diagnosisId

        val fetched = client.get()
            .uri("/v1/diagnostics/{id}", diagnosisId)
            .exchange()
            .expectStatus().isOk
            .expectBody(DiagnosisResponse::class.java)
            .returnResult()
            .responseBody

        assertThat(fetched).isNotNull
        assertThat(fetched!!.issues).isNotEmpty
        assertThat(fetched.diagnosisId).isEqualTo(diagnosisId)
    }
}
