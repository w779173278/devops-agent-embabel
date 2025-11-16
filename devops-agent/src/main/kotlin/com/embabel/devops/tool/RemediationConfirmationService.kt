package com.embabel.devops.tool

import com.embabel.devops.config.DevOpsAgentProperties
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service

@Service
/** 确认口令窗口：确保危险命令只在有效期内执行。 */
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
            error("缺少确认口令：${expected}")
        }
    }

    private fun approvalKey(conversationId: String, phrase: String) = "$conversationId|$phrase"
}
