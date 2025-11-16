/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.example

import com.embabel.agent.config.annotation.EnableAgents
import com.embabel.agent.config.annotation.McpServers
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * 将 Embabel Agent 作为 MCP（Model Context Protocol）服务器运行的 Spring Boot 应用。
 *
 * 该应用把 Agent 暴露为兼容 MCP 的工具，供 Claude Desktop、IDE 等客户端调用，
 * 并通过 JSON-RPC 协议实现工具发现与执行。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(
    basePackages = ["com.embabel.example"]
)
@EnableAgents(
    mcpServers = [McpServers.DOCKER_DESKTOP, McpServers.DOCKER],
)
class KotlinAgentMcpServerApplication

/**
 * 应用入口，用于启动 MCP 服务器。
 *
 * 通过 MCP 相关配置启动 Spring Boot，并开始监听来自 MCP 客户端的 JSON-RPC 请求。
 *
 * @param args 传递给应用的命令行参数
 */
fun main(args: Array<String>) {
    runApplication<KotlinAgentMcpServerApplication>(*args)
}
