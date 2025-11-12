package com.embabel.devops.service

import com.embabel.devops.config.DevOpsAgentProperties
import org.springframework.stereotype.Service

@Service
class DataSourceRegistry(
    private val properties: DevOpsAgentProperties,
) {
    fun findByType(type: DevOpsAgentProperties.DataSourceType): List<DevOpsAgentProperties.DataSourceConfig> =
        properties.dataSources.filter { it.type == type }

    fun primary(type: DevOpsAgentProperties.DataSourceType): DevOpsAgentProperties.DataSourceConfig? =
        findByType(type).firstOrNull()
}
