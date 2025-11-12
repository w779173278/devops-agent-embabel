package com.embabel.devops.service

import com.embabel.devops.config.DevOpsAgentProperties
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service

@Service
class RemediationConfirmationService(
    private val properties: DevOpsAgentProperties,
) {
    private val approvals = ConcurrentHashMap<String, Instant>()

    fun requireConfirmation(conversationId: String, phrase: String?, expected: String) {
        if (!properties.safety.requireApproval) {
            return
        }
        if (phrase == expected) {
            approvals[approvalKey(conversationId, expected)] = Instant.now()
            return
        }
        val timestamp = approvals[approvalKey(conversationId, expected)]
        val withinWindow = timestamp?.isAfter(
            Instant.now().minus(properties.safety.confirmationWindowMinutes, ChronoUnit.MINUTES)
        ) == true
        if (!withinWindow) {
            error("Missing confirmation phrase for ${expected}")
        }
    }

    private fun approvalKey(conversationId: String, phrase: String) = "$conversationId|$phrase"
}
