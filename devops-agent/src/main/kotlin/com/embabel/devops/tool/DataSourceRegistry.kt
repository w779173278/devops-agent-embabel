package com.embabel.devops.tool

import com.embabel.devops.config.DevOpsAgentProperties
import org.springframework.stereotype.Service

@Service
/** 简易数据源注册表，提供默认日志/指标源。 */
class DataSourceRegistry(
    private val properties: DevOpsAgentProperties,
) {
    fun findByType(type: DevOpsAgentProperties.DataSourceType): List<DevOpsAgentProperties.DataSourceConfig> =
        properties.dataSources.filter { it.type == type }

    fun primary(type: DevOpsAgentProperties.DataSourceType): DevOpsAgentProperties.DataSourceConfig? =
        findByType(type).firstOrNull()
}
