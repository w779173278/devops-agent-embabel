package com.embabel.devops

import com.embabel.agent.config.annotation.EnableAgents
import com.embabel.agent.config.annotation.LoggingThemes
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableAgents(loggingTheme = LoggingThemes.SEVERANCE)
@ConfigurationPropertiesScan("com.embabel.devops.config")
class DevOpsAgentShellApplication

fun main(args: Array<String>) {
    runApplication<DevOpsAgentShellApplication>(*args)
}
