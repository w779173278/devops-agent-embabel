package com.embabel.devops.agent

import com.embabel.devops.DevOpsAgentTestConfiguration
import com.embabel.devops.model.DiagnosisRequest
import com.embabel.devops.model.DiagnosisResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [DevOpsAgentTestConfiguration::class])
class ConversationMemoryServiceTest @Autowired constructor(
    private val conversationMemoryService: ConversationMemoryService,
) {

    @Test
    fun `should persist conversation state for ttl window`() {
        val request = DiagnosisRequest(
            conversationId = "conv-memory",
            service = "checkout",
            environment = "prod",
            windowMinutes = 15,
            symptom = "timeouts",
        )
        val result = DiagnosisResult(
            request = request,
            issues = emptyList(),
            suggestedActions = emptyList(),
        )
        conversationMemoryService.rememberDiagnosis(result)
        val state = conversationMemoryService.read("conv-memory")
        assertThat(state?.service).isEqualTo("checkout")
        conversationMemoryService.rememberAction("conv-memory", "restart-checkout")
        val updated = conversationMemoryService.read("conv-memory")
        assertThat(updated?.actionsExecuted).contains("restart-checkout")
    }
}
