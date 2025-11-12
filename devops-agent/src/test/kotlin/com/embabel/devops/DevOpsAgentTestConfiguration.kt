package com.embabel.devops

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.ComponentScan

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan("com.embabel.devops")
@ConfigurationPropertiesScan("com.embabel.devops.config")
class DevOpsAgentTestConfiguration
