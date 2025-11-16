package com.embabel.devops.agent

import com.embabel.devops.DevOpsAgentTestConfiguration
import com.embabel.devops.model.DiagnosisRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [DevOpsAgentTestConfiguration::class])
class DiagnosisAgentTest @Autowired constructor(
    private val diagnosisAgent: DiagnosisAgent,
    private val conversationMemoryService: ConversationMemoryService,
) {

    @Test
    fun `should aggregate logs and metrics into diagnosis`() {
        val request = DiagnosisRequest(
            conversationId = "conv-1",
            service = "checkout",
            environment = "prod",
            windowMinutes = 30,
            symptom = "elevated 5xx",
        )

        val bundle = diagnosisAgent.collectSignals(request)
        val result = diagnosisAgent.analyze(bundle, request)
        val summary = diagnosisAgent.summarize(result)

        assertThat(result.issues).isNotEmpty
        assertThat(summary.content).contains("Diagnosis")
        val memory = conversationMemoryService.read("conv-1")
        assertThat(memory?.lastDiagnosisId).isEqualTo(result.id)
        assertThat(bundle.logs).isNotEmpty
        assertThat(bundle.metrics).isNotEmpty
    }
}
