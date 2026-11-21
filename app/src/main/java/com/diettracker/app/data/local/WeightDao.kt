package com.diettracker.app.data.local

import com.diettracker.app.model.WeightRecord
import kotlinx.coroutines.flow.Flow

interface WeightDao {
    fun getAllWeightRecords(): Flow<List<WeightRecord>>
    suspend fun getAllWeightRecordsSync(): List<WeightRecord>
    fun getWeightRecordByDate(date: String): Flow<WeightRecord?>
    suspend fun getWeightRecordByDateSync(date: String): WeightRecord?
    suspend fun getLatestWeightRecordSync(): WeightRecord?
    suspend fun insertOrUpdate(record: WeightRecord)
    suspend fun deleteWeightRecord(date: String)
}
