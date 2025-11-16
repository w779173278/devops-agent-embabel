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
import com.embabel.agent.config.annotation.LoggingThemes
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * 以交互式 Shell 方式运行 Embabel Agent 的 Spring Boot 应用。
 *
 * 该应用提供命令行界面，便于测试与调试 Agent：可以执行 Agent 命令、进入聊天模式，
 * 以及排查工作流。
 *
 * ## 使用示例
 * ```
 * shell:> execute "Find news for Alice who is a Gemini"
 * shell:> chat
 * shell:> help
 * ```
 *
 * @see EnableAgents
 */
@SpringBootApplication
@ConfigurationPropertiesScan(
    basePackages = ["com.embabel.example"]
)
@EnableAgents(
    loggingTheme = LoggingThemes.SEVERANCE,
)
class KotlinAgentShellMcpClientApplication

/**
 * 应用入口，负责启动 Spring Boot。
 *
 * 初始化带有 Agent 自动配置的 Spring 上下文，并开启交互式 Shell 界面。
 *
 * @param args 传递给应用的命令行参数
 */
fun main(args: Array<String>) {
    runApplication<KotlinAgentShellMcpClientApplication>(*args)
}
