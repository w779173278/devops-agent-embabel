package com.embabel.devops.config

import org.springframework.boot.context.properties.ConfigurationProperties
@ConfigurationProperties(prefix = "actions")
data class ActionCatalogProperties(
    val definitions: List<ActionDefinition> = emptyList(),
) {
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
