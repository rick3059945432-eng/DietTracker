package com.diettracker.app.data.local

import com.diettracker.app.model.DailySummary
import com.diettracker.app.model.DateCalendarInfo
import com.diettracker.app.model.DietGoalMode
import kotlinx.coroutines.flow.Flow

interface DailySummaryDao {
    fun getDailySummary(date: String): Flow<DailySummary?>
    suspend fun getDailySummarySync(date: String): DailySummary?
    suspend fun insertOrUpdate(summary: DailySummary)
    fun getRecordedDateCalendarInfo(fallbackMode: DietGoalMode): Flow<Map<String, DateCalendarInfo>>
}
