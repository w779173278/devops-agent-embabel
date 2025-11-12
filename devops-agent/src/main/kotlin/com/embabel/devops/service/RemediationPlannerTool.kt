package com.embabel.devops.service

import org.springframework.stereotype.Service

@Service
class RemediationPlannerTool(
    private val planService: ActionPlanService,
) {
    fun plan(diagnosisResult: DiagnosisResult): ActionPlan = planService.createPlan(diagnosisResult)
}
