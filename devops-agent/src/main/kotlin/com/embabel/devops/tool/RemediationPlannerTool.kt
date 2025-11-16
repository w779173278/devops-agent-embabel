package com.embabel.devops.tool

import com.embabel.devops.model.ActionPlan
import com.embabel.devops.model.ActionPlanService
import com.embabel.devops.model.DiagnosisResult
import org.springframework.stereotype.Service

@Service
class RemediationPlannerTool(
    private val planService: ActionPlanService,
) {
    fun plan(diagnosisResult: DiagnosisResult): ActionPlan = planService.createPlan(diagnosisResult)
}
