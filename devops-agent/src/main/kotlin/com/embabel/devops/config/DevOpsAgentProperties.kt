package com.embabel.devops.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * DevOps Agent 运行期的全局配置（来自 `application.yml`）。
 */
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
    /**
     * 工作流默认值：默认环境与是否推送进度通知。
     */
    data class WorkflowProperties(
        @DefaultValue("prod")
        val defaultEnvironment: String = "prod",
        @DefaultValue("true")
        val progressNotifications: Boolean = true,
    )

    /**
     * 安全策略：确认口令、有效期和告警渠道。
     */
    data class SafetyProperties(
        @DefaultValue("true")
        val requireApproval: Boolean = true,
        @DefaultValue("10")
        val confirmationWindowMinutes: Long = 10,
        val escalationChannel: String = "#sre-oncall",
    )

    /**
     * 数据源配置：描述日志/指标等外部资源及其环境。
     */
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
