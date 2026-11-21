package com.diettracker.app.util

import com.diettracker.app.model.FatLossRate
import com.diettracker.app.model.UserProfile
import com.diettracker.app.model.WeightRecord
import com.diettracker.app.model.WeeklyReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class AdaptiveTdeeCalculatorTest {

    private val testProfile = UserProfile(
        nickname = "自律测试者",
        gender = "男",
        age = 26,
        height = 175f,
        weight = 70f // BMR = 10*70 + 6.25*175 - 5*26 + 5 = 700 + 1093.75 - 130 + 5 = 1668.75 -> 1668
    )

    @Test
    fun testColdStartProtection_whenLessThan14Days() {
        val today = "2026-11-21"
        // 只有 5 天历史
        val datesWithDiet = setOf("2026-11-17", "2026-11-18", "2026-11-19", "2026-11-20", "2026-11-21")
        val weightRecords = listOf(
            WeightRecord(date = "2026-11-17", weight = 70f),
            WeightRecord(date = "2026-11-19", weight = 69.8f),
            WeightRecord(date = "2026-11-21", weight = 69.7f)
        )
        val past7DaysIntake = datesWithDiet.associateWith { 1800 }

        val status = AdaptiveTdeeCalculator.evaluateWeeklyReview(
            todayStr = today,
            profile = testProfile,
            rate = FatLossRate.STANDARD,
            currentTargetCalories = 1800,
            allDatesWithDiet = datesWithDiet,
            allWeightRecords = weightRecords,
            past7DaysIntakeMap = past7DaysIntake
        )

        assertTrue("不足14天必须返回 ColdStart", status is WeeklyReviewStatus.ColdStart)
        val coldStart = status as WeeklyReviewStatus.ColdStart
        assertEquals(14, coldStart.requiredDays)
        assertTrue(coldStart.daysSinceFirstRecord in 1..13)
    }

    @Test
    fun testInsufficientDataProtection_whenDietDaysLessThan5() {
        val today = "2026-11-21"
        // 历史超过14天
        val allDates = setOf("2026-11-01", "2026-11-18", "2026-11-19", "2026-11-20") // 过去7天仅有3天有效饮食
        val weightRecords = listOf(
            WeightRecord(date = "2026-11-01", weight = 71f),
            WeightRecord(date = "2026-11-16", weight = 70.5f),
            WeightRecord(date = "2026-11-18", weight = 70.3f),
            WeightRecord(date = "2026-11-20", weight = 70.2f),
            WeightRecord(date = "2026-11-21", weight = 70.1f)
        )
        val past7DaysIntake = mapOf(
            "2026-11-18" to 1900,
            "2026-11-19" to 1850,
            "2026-11-20" to 1800
        )

        val status = AdaptiveTdeeCalculator.evaluateWeeklyReview(
            todayStr = today,
            profile = testProfile,
            rate = FatLossRate.STANDARD,
            currentTargetCalories = 1900,
            allDatesWithDiet = allDates,
            allWeightRecords = weightRecords,
            past7DaysIntakeMap = past7DaysIntake
        )

        assertTrue("饮食少于5天必须返回 InsufficientData", status is WeeklyReviewStatus.InsufficientData)
        val insufficient = status as WeeklyReviewStatus.InsufficientData
        assertEquals(3, insufficient.validDietDays)
        assertEquals(5, insufficient.requiredDietDays)
    }

    @Test
    fun testReadyReport_andMaxAdjustmentStepLimit() {
        val today = "2026-11-21"
        val pastDates = AdaptiveTdeeCalculator.getPastDatesList(today, 20, java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()))
        val allDatesWithDiet = pastDates.toSet()

        // 过去 7 天每天吃 2400 kcal，体重甚至微涨 0.2kg（说明实际 TDEE 显著低于 2400）
        val past7Days = pastDates.takeLast(7)
        val past7DaysIntake = past7Days.associateWith { 2400 }

        val weightRecords = pastDates.mapIndexed { idx, d ->
            WeightRecord(date = d, weight = 70f + idx * 0.02f)
        }

        val status = AdaptiveTdeeCalculator.evaluateWeeklyReview(
            todayStr = today,
            profile = testProfile,
            rate = FatLossRate.AGGRESSIVE, // 进取减脂需要很大缺口
            currentTargetCalories = 2400,
            allDatesWithDiet = allDatesWithDiet,
            allWeightRecords = weightRecords,
            past7DaysIntakeMap = past7DaysIntake
        )

        assertTrue("满足所有条件时必须返回 Ready", status is WeeklyReviewStatus.Ready)
        val report = (status as WeeklyReviewStatus.Ready).report

        // 验证单次调整步长上限绝对不超过 150 kcal
        assertTrue(
            "单次调整幅度绝对值不能超过 150 kcal，当前为 ${report.calorieAdjustment}",
            abs(report.calorieAdjustment) <= AdaptiveTdeeCalculator.MAX_CALORIE_ADJUSTMENT_STEP
        )
        assertEquals(2400 - 150, report.suggestedTargetCalories)
    }

    @Test
    fun testSafetyFloorProtection_doesNotDropBelowBmr() {
        val today = "2026-11-21"
        val pastDates = AdaptiveTdeeCalculator.getPastDatesList(today, 20, java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()))
        val past7Days = pastDates.takeLast(7)
        val past7DaysIntake = past7Days.associateWith { 1700 }

        val weightRecords = pastDates.map {
            WeightRecord(date = it, weight = 70f)
        }

        val status = AdaptiveTdeeCalculator.evaluateWeeklyReview(
            todayStr = today,
            profile = testProfile,
            rate = FatLossRate.AGGRESSIVE,
            currentTargetCalories = 1700,
            allDatesWithDiet = pastDates.toSet(),
            allWeightRecords = weightRecords,
            past7DaysIntakeMap = past7DaysIntake
        )

        assertTrue(status is WeeklyReviewStatus.Ready)
        val report = (status as WeeklyReviewStatus.Ready).report
        val bmr = testProfile.bmr

        assertTrue(
            "推荐热量 (${report.suggestedTargetCalories}) 绝对不能低于安全底线 (${report.safetyFloor})",
            report.suggestedTargetCalories >= report.safetyFloor
        )
        assertTrue(report.suggestedTargetCalories >= bmr)
    }

    @Test
    fun testPlateauProtection_whenAtSafetyFloorAndNoWeightChange() {
        val today = "2026-11-21"
        val pastDates = AdaptiveTdeeCalculator.getPastDatesList(today, 25, java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()))
        val past7Days = pastDates.takeLast(7)
        val safetyFloor = max(testProfile.bmr, 1500)
        // 当前热量已经在安全底线
        val currentTarget = safetyFloor
        val past7DaysIntake: Map<String, Int> = past7Days.associateWith { currentTarget }

        // 连续两周体重完全没变 (70.0kg)
        val weightRecords = pastDates.map {
            WeightRecord(date = it, weight = 70.0f, waist = 78f, bodyFat = 18f)
        }

        val status = AdaptiveTdeeCalculator.evaluateWeeklyReview(
            todayStr = today,
            profile = testProfile,
            rate = FatLossRate.STANDARD,
            currentTargetCalories = currentTarget,
            allDatesWithDiet = pastDates.toSet(),
            allWeightRecords = weightRecords,
            past7DaysIntakeMap = past7DaysIntake,
            past14DaysWeightRecords = weightRecords.takeLast(14)
        )

        assertTrue(status is WeeklyReviewStatus.Ready)
        val report = (status as WeeklyReviewStatus.Ready).report

        assertTrue("在底线且两周无变化时必须标记平台期 isPlateauAtFloor", report.isPlateauAtFloor)
        assertTrue("平台期不能继续往下扣减热量", report.calorieAdjustment >= 0)
        assertNotNull("平台期必须提供专属破局指导建议", report.plateauAdvice)
    }
}
