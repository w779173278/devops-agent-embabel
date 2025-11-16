package com.embabel.devops.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 修复动作目录配置，对应 `actions.yml`，用于描述可执行命令及回滚信息。
 */
@ConfigurationProperties(prefix = "actions")
data class ActionCatalogProperties(
    val definitions: List<ActionDefinition> = emptyList(),
) {
    /**
     * 单个动作定义——供 Agent 生成修复计划以及要求确认口令。
     */
    data class ActionDefinition(
        val id: String,
        val description: String,
        val commandTemplate: String,
        val confirmationPhrase: String,
        val ruleIds: List<String> = emptyList(),
        val rollbackCommand: String? = null,
        val dryRunSupported: Boolean = true,
    )
}
