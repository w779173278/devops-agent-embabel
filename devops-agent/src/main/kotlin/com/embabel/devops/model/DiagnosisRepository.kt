package com.embabel.devops.model

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service

@Service
class DiagnosisRepository {
    private val results = ConcurrentHashMap<String, DiagnosisResult>()

    fun save(result: DiagnosisResult): DiagnosisResult {
        results[result.id] = result
        return result
    }

    fun findById(id: String): DiagnosisResult? = results[id]
}
