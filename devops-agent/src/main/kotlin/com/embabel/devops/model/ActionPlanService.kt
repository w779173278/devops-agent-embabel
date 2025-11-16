package com.embabel.devops.model

import com.embabel.devops.config.ActionCatalogProperties
import org.springframework.stereotype.Service

@Service
/**
 * 基于诊断结果匹配到动作目录，生成唯一的修复计划。
 */
class ActionPlanService(
    private val catalog: ActionCatalogProperties,
) {
    fun createPlan(diagnosis: DiagnosisResult): ActionPlan {
        val steps = diagnosis.issues.flatMap { issue ->
            catalog.definitions
                .filter { definition -> issue.ruleId in definition.ruleIds }
                .map { definition ->
                    ActionStep(
                        actionId = definition.id,
                        description = definition.description,
                        command = definition.commandTemplate,
                        confirmationPhrase = definition.confirmationPhrase,
                        dryRunSupported = definition.dryRunSupported,
                        rollbackCommand = definition.rollbackCommand,
                    )
                }
        }.distinctBy { it.actionId }
        return ActionPlan(
            diagnosisId = diagnosis.id,
            steps = steps,
        )
    }
}
