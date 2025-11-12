package com.embabel.devops.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
@ConfigurationProperties(prefix = "devops")
data class DevOpsAgentProperties(
    @DefaultValue("embabel")
    val organization: String,
    @DefaultValue("60")
    val conversationTtlMinutes: Long,
    val workflow: WorkflowProperties = WorkflowProperties(),
    val safety: SafetyProperties = SafetyProperties(),
    val dataSources: List<DataSourceConfig> = emptyList(),
) {
    data class WorkflowProperties(
        @DefaultValue("prod")
        val defaultEnvironment: String = "prod",
        @DefaultValue("true")
        val progressNotifications: Boolean = true,
    )

    data class SafetyProperties(
        @DefaultValue("true")
        val requireApproval: Boolean = true,
        @DefaultValue("10")
        val confirmationWindowMinutes: Long = 10,
        val escalationChannel: String = "#sre-oncall",
    )

    data class DataSourceConfig(
        val id: String,
        val type: DataSourceType,
        val endpoint: String,
        val environment: String,
        val description: String,
    )

    enum class DataSourceType {
        LOGS,
        METRICS,
    }
}
