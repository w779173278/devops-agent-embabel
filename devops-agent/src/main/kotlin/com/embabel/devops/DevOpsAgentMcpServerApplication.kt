package com.embabel.devops

import com.embabel.agent.config.annotation.EnableAgents
import com.embabel.agent.config.annotation.LoggingThemes
import com.embabel.agent.config.annotation.McpServers
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableAgents(
    loggingTheme = LoggingThemes.SEVERANCE,
//    mcpServers = [McpServers.DOCKER_DESKTOP, McpServers.DOCKER],
)
@ConfigurationPropertiesScan("com.embabel.devops.config")
class DevOpsAgentMcpServerApplication

fun main(args: Array<String>) {
    runApplication<DevOpsAgentMcpServerApplication>(*args)
}
