package com.embabel.devops.model

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service

@Service
class ExecutionRecordRepository {
    private val records = ConcurrentHashMap<String, ExecutionRecord>()

    fun save(record: ExecutionRecord): ExecutionRecord {
        records[record.executionId] = record
        return record
    }

    fun findById(executionId: String): ExecutionRecord? = records[executionId]
}
