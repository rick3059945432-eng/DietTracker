package com.diettracker.app.util

import com.diettracker.app.model.FatLossRate
import com.diettracker.app.model.UserProfile
import com.diettracker.app.model.WeightRecord
import com.diettracker.app.model.WeeklyReviewReport
import com.diettracker.app.model.WeeklyReviewStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * 周级复盘与自适应 TDEE 校准计算器（纯函数无副作用模块）
 */
object AdaptiveTdeeCalculator {

    const val COLD_START_DAYS = 14
    const val REQUIRED_DIET_DAYS = 5
    const val REQUIRED_WEIGHT_DAYS = 3
    const val MAX_CALORIE_ADJUSTMENT_STEP = 150 // 每周调整步长上限 ±150 kcal
    const val KCAL_PER_KG_FAT = 7700f           // 1kg 纯脂肪对应能量估算值

    /**
     * 评估周级复盘状态与计算校准建议
     */
    fun evaluateWeeklyReview(
        todayStr: String,
        profile: UserProfile,
        rate: FatLossRate,
        currentTargetCalories: Int,
        allDatesWithDiet: Set<String>,
        allWeightRecords: List<WeightRecord>,
        past7DaysIntakeMap: Map<String, Int>,
        past14DaysWeightRecords: List<WeightRecord> = emptyList()
    ): WeeklyReviewStatus {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayDate = parseDate(todayStr, dateFormat) ?: Date()

        // 1. 冷启动判断：寻找最早有效记录（饮食或体重）
        val earliestDietDate = allDatesWithDiet.minOrNull()
        val earliestWeightDate = allWeightRecords.minOfOrNull { it.date }
        val earliestDateStr = when {
            earliestDietDate != null && earliestWeightDate != null -> minOf(earliestDietDate, earliestWeightDate)
            earliestDietDate != null -> earliestDietDate
            earliestWeightDate != null -> earliestWeightDate
            else -> null
        }

        val daysSinceFirst = if (earliestDateStr != null) {
            val earliestDate = parseDate(earliestDateStr, dateFormat)
            if (earliestDate != null) {
                val diffDays = ((todayDate.time - earliestDate.time) / (1000L * 60 * 60 * 24)).toInt() + 1
                max(1, diffDays)
            } else 1
        } else 0

        if (daysSinceFirst < COLD_START_DAYS) {
            return WeeklyReviewStatus.ColdStart(
                daysSinceFirstRecord = daysSinceFirst,
                requiredDays = COLD_START_DAYS
            )
        }

        // 2. 过去 7 天区间及有效数据统计
        val past7Dates = getPastDatesList(todayStr, 7, dateFormat)
        val startDateStr = past7Dates.first()
        val endDateStr = past7Dates.last()

        val validDietDaysCount = past7Dates.count { (past7DaysIntakeMap[it] ?: 0) > 0 }
        val past7WeightRecords = allWeightRecords.filter { it.date in past7Dates }
        val validWeightDaysCount = past7WeightRecords.size

        if (validDietDaysCount < REQUIRED_DIET_DAYS || validWeightDaysCount < REQUIRED_WEIGHT_DAYS) {
            return WeeklyReviewStatus.InsufficientData(
                validDietDays = validDietDaysCount,
                requiredDietDays = REQUIRED_DIET_DAYS,
                validWeightDays = validWeightDaysCount,
                requiredWeightDays = REQUIRED_WEIGHT_DAYS
            )
        }

        // 3. 计算日均摄入热量
        val validIntakes = past7Dates.mapNotNull {
            val cal = past7DaysIntakeMap[it] ?: 0
            if (cal > 0) cal else null
        }
        val avgDailyIntake = if (validIntakes.isNotEmpty()) validIntakes.average().toInt() else currentTargetCalories

        // 4. 计算 EMA 趋势体重变化
        val allTrendPoints = WeightTrendUtils.computeEMA(allWeightRecords)
        val startTrendPoint = allTrendPoints.lastOrNull { it.record.date <= startDateStr } ?: allTrendPoints.first()
        val endTrendPoint = allTrendPoints.lastOrNull { it.record.date <= endDateStr } ?: allTrendPoints.last()

        val startTrendWeight = startTrendPoint.trendWeight
        val endTrendWeight = endTrendPoint.trendWeight
        val trendDeltaKg = endTrendWeight - startTrendWeight

        val startDateObj = parseDate(startTrendPoint.record.date, dateFormat) ?: todayDate
        val endDateObj = parseDate(endTrendPoint.record.date, dateFormat) ?: todayDate
        val trendDays = max(1, ((endDateObj.time - startDateObj.time) / (1000L * 60 * 60 * 24)).toInt())

        // 5. 反推真实 TDEE（能量守恒公式：TDEE = 摄入 - (体重变化量 * 7700 / 天数)）
        val rawEstimatedTDEE = (avgDailyIntake - (trendDeltaKg * KCAL_PER_KG_FAT / trendDays)).toInt()
        val baselineMaintenance = profile.maintenanceCalories

        // 生理上下限安全保护：[BMR * 1.0, BMR * 2.2]
        val minPhysiologicalTdee = (profile.bmr * 1.0f).toInt()
        val maxPhysiologicalTdee = (profile.bmr * 2.2f).toInt()
        val clampedTdee = rawEstimatedTDEE.coerceIn(minPhysiologicalTdee, maxPhysiologicalTdee)

        // 权重平滑（70% 本周实测反推 + 30% 理论基线锚定）
        val estimatedTDEE = (0.7f * clampedTdee + 0.3f * baselineMaintenance).toInt()

        // 6. 目标速率与理想新热量计算
        val currentWeight = endTrendWeight
        val targetWeeklyLossKg = currentWeight * rate.weeklyWeightRatio
        val targetDailyDeficit = (targetWeeklyLossKg * KCAL_PER_KG_FAT / 7f).toInt()
        val idealNewTarget = estimatedTDEE - targetDailyDeficit

        // 7. 三大安全护栏
        // 护栏 1：单次调整步长限制在 ±150 kcal
        val stepClampedTarget = idealNewTarget.coerceIn(
            currentTargetCalories - MAX_CALORIE_ADJUSTMENT_STEP,
            currentTargetCalories + MAX_CALORIE_ADJUSTMENT_STEP
        )

        // 护栏 2：绝对安全底线（不能低于 BMR 且男>=1500 / 女>=1200）
        val genderFloor = if (profile.gender == "女") 1200 else 1500
        val safetyFloor = max(profile.bmr, genderFloor)

        // 护栏 3：平台期检测（连续 2 周趋势体重变化极小且已触及安全底线）
        val twoWeekDelta = calculateTwoWeeksTrendDelta(allTrendPoints, past14DaysWeightRecords, todayStr, dateFormat)
        val isAtFloor = currentTargetCalories <= safetyFloor + 50
        val isPlateauAtFloor = abs(twoWeekDelta) < 0.2f && isAtFloor && rate != FatLossRate.MAINTAIN

        var suggestedTargetCalories = max(safetyFloor, stepClampedTarget)
        var plateauAdvice: String? = null

        if (isPlateauAtFloor) {
            // 平台期且已触底：严禁继续扣减热量
            suggestedTargetCalories = max(currentTargetCalories, safetyFloor)
            plateauAdvice = "当前摄入已触及身体基础代谢安全底线（${safetyFloor} kcal），严禁盲目削减热量！平台期破局策略：\n" +
                    "1. 核对过去两周腰围或体脂率是否已缩小（排除体成分重组或水分滞留）；\n" +
                    "2. 增加日常非运动消耗（NEAT，如每天多步行 2000~3000 步）；\n" +
                    "3. 安排 3~7 天维持热量休息期（Diet Break）唤醒受抑代谢。"
        }

        val calorieAdjustment = suggestedTargetCalories - currentTargetCalories

        // 8. 辅助体测变化分析（腰围与体脂率）
        val (waistDelta, bodyFatDelta) = calculateBodyMeasurementDeltas(past14DaysWeightRecords)

        // 9. 智能解释说明文案生成
        val explanation = buildExplanation(
            rate = rate,
            trendDeltaKg = trendDeltaKg,
            targetWeeklyLossKg = targetWeeklyLossKg,
            calorieAdjustment = calorieAdjustment,
            isPlateauAtFloor = isPlateauAtFloor,
            isAtSafetyFloor = suggestedTargetCalories <= safetyFloor,
            safetyFloor = safetyFloor
        )

        val report = WeeklyReviewReport(
            startDate = startDateStr,
            endDate = endDateStr,
            validDietDays = validDietDaysCount,
            validWeightDays = validWeightDaysCount,
            avgDailyIntake = avgDailyIntake,
            startTrendWeight = startTrendWeight,
            endTrendWeight = endTrendWeight,
            trendDeltaKg = trendDeltaKg,
            targetWeeklyLossKg = targetWeeklyLossKg,
            estimatedTDEE = estimatedTDEE,
            baselineMaintenanceCalories = baselineMaintenance,
            currentTargetCalories = currentTargetCalories,
            suggestedTargetCalories = suggestedTargetCalories,
            calorieAdjustment = calorieAdjustment,
            rate = rate,
            explanation = explanation,
            safetyFloor = safetyFloor,
            isAtSafetyFloor = suggestedTargetCalories <= safetyFloor,
            isPlateauAtFloor = isPlateauAtFloor,
            plateauAdvice = plateauAdvice,
            waistDelta = waistDelta,
            bodyFatDelta = bodyFatDelta
        )

        return WeeklyReviewStatus.Ready(report)
    }

    private fun buildExplanation(
        rate: FatLossRate,
        trendDeltaKg: Float,
        targetWeeklyLossKg: Float,
        calorieAdjustment: Int,
        isPlateauAtFloor: Boolean,
        isAtSafetyFloor: Boolean,
        safetyFloor: Int
    ): String {
        if (isPlateauAtFloor) {
            return "体重连续两周平稳且热量已达安全底线，当前不建议继续降低热量，请保持当前摄入并通过打破平台期指南调整。"
        }

        val deltaStr = String.format(Locale.getDefault(), "%.2f", abs(trendDeltaKg))
        val targetStr = String.format(Locale.getDefault(), "%.2f", targetWeeklyLossKg)

        val speedDesc = if (trendDeltaKg < 0) {
            "本周趋势体重下降了 ${deltaStr} kg（目标约 ${targetStr} kg）"
        } else if (trendDeltaKg > 0.05f) {
            "本周趋势体重略微浮动了 +${deltaStr} kg"
        } else {
            "本周趋势体重基本平稳（变化仅 ${deltaStr} kg）"
        }

        val adjustDesc = when {
            calorieAdjustment < 0 -> "建议每日热量温和微调 ${calorieAdjustment} kcal，帮助身体重新建立最佳减脂缺口。"
            calorieAdjustment > 0 -> "实际减重速度略快于预期目标，建议温和回调 +${calorieAdjustment} kcal，更好地守护肌肉储备与体能。"
            else -> "当前减脂与能量节奏非常契合预期，建议保持当前热量目标。"
        }

        val safetyDesc = if (isAtSafetyFloor) "（已触发 BMR 代谢安全底线保护：${safetyFloor} kcal）" else ""

        return "$speedDesc。根据过去一周的真实能量收支比，$adjustDesc$safetyDesc"
    }

    private fun calculateTwoWeeksTrendDelta(
        allTrendPoints: List<WeightTrendUtils.TrendPoint>,
        past14DaysRecords: List<WeightRecord>,
        todayStr: String,
        dateFormat: SimpleDateFormat
    ): Float {
        if (allTrendPoints.size < 2) return 0f
        val latest = allTrendPoints.last()
        val latestDate = parseDate(latest.record.date, dateFormat) ?: return 0f
        val fourteenDaysMs = 14L * 24 * 60 * 60 * 1000L

        var targetPoint: WeightTrendUtils.TrendPoint? = null
        var bestDiff = Long.MAX_VALUE

        for (p in allTrendPoints) {
            if (p == latest) continue
            val d = parseDate(p.record.date, dateFormat) ?: continue
            val diff = latestDate.time - d.time
            if (diff in (10L * 24 * 60 * 60 * 1000L)..(18L * 24 * 60 * 60 * 1000L)) {
                val distance = abs(diff - fourteenDaysMs)
                if (distance < bestDiff) {
                    bestDiff = distance
                    targetPoint = p
                }
            }
        }

        val pastPoint = targetPoint ?: allTrendPoints.first()
        return latest.trendWeight - pastPoint.trendWeight
    }

    private fun calculateBodyMeasurementDeltas(records: List<WeightRecord>): Pair<Float?, Float?> {
        val sorted = records.sortedBy { it.date }
        val waistRecords = sorted.filter { it.waist != null }
        val waistDelta = if (waistRecords.size >= 2) {
            waistRecords.last().waist!! - waistRecords.first().waist!!
        } else null

        val bodyFatRecords = sorted.filter { it.bodyFat != null }
        val bodyFatDelta = if (bodyFatRecords.size >= 2) {
            bodyFatRecords.last().bodyFat!! - bodyFatRecords.first().bodyFat!!
        } else null

        return Pair(waistDelta, bodyFatDelta)
    }

    fun getPastDatesList(endDateStr: String, count: Int, dateFormat: SimpleDateFormat): List<String> {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val endDate = parseDate(endDateStr, dateFormat) ?: Date()
        cal.time = endDate
        cal.add(Calendar.DAY_OF_YEAR, -(count - 1))

        for (i in 0 until count) {
            list.add(dateFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list
    }

    private fun parseDate(dateStr: String, dateFormat: SimpleDateFormat): Date? {
        return try {
            dateFormat.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }
}
