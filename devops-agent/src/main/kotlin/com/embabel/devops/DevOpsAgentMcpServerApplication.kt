package com.embabel.devops

import com.embabel.agent.api.annotation.EnableAgentMcpServer
import com.embabel.agent.api.annotation.EnableAgents
import com.embabel.agent.api.logging.LoggingThemes
import com.embabel.agent.mcp.McpServers
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableAgentMcpServer
@EnableAgents(
    loggingTheme = LoggingThemes.SEVERANCE,
    mcpServers = [McpServers.DOCKER_DESKTOP]
)
@ConfigurationPropertiesScan("com.embabel.devops.config")
class DevOpsAgentMcpServerApplication

fun main(args: Array<String>) {
    runApplication<DevOpsAgentMcpServerApplication>(*args)
}
