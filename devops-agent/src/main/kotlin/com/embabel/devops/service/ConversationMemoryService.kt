package com.embabel.devops.service

import com.embabel.devops.config.DevOpsAgentProperties
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service

@Service
class ConversationMemoryService(
    private val properties: DevOpsAgentProperties,
) {
    private val state = ConcurrentHashMap<String, ConversationState>()

    fun rememberDiagnosis(result: DiagnosisResult) {
        val current = state.getOrPut(result.request.conversationId) {
            ConversationState(conversationId = result.request.conversationId)
        }
        state[result.request.conversationId] = current.copy(
            service = result.request.service,
            environment = result.request.environment,
            lastDiagnosisId = result.id,
            lastUpdated = Instant.now(),
        )
    }

    fun rememberAction(conversationId: String, actionId: String) {
        val current = state.getOrPut(conversationId) { ConversationState(conversationId = conversationId) }
        val updatedActions = current.actionsExecuted + actionId
        state[conversationId] = current.copy(
            actionsExecuted = updatedActions,
            lastUpdated = Instant.now(),
        )
    }

    fun read(conversationId: String): ConversationState? = state[conversationId]?.takeIf { snapshot ->
        val ttl = properties.conversationTtlMinutes
        snapshot.lastUpdated.isAfter(Instant.now().minusSeconds(ttl * 60))
    }
}

data class ConversationState(
    val conversationId: String,
    val service: String? = null,
    val environment: String? = null,
    val lastDiagnosisId: String? = null,
    val actionsExecuted: List<String> = emptyList(),
    val lastUpdated: Instant = Instant.now(),
)
