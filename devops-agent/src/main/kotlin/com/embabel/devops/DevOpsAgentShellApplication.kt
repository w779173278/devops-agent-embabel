package com.embabel.devops

import com.embabel.agent.api.annotation.EnableAgentShell
import com.embabel.agent.api.annotation.EnableAgents
import com.embabel.agent.api.logging.LoggingThemes
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableAgentShell
@EnableAgents(loggingTheme = LoggingThemes.SEVERANCE)
@ConfigurationPropertiesScan("com.embabel.devops.config")
class DevOpsAgentShellApplication

fun main(args: Array<String>) {
    runApplication<DevOpsAgentShellApplication>(*args)
}
