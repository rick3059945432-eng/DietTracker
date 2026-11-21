package com.diettracker.app.model

/**
 * 目标减脂速率
 */
enum class FatLossRate(
    val displayName: String,
    val weeklyWeightRatio: Float,
    val description: String
) {
    MAINTAIN(
        displayName = "维持体重",
        weeklyWeightRatio = 0.0f,
        description = "能量收支平衡，稳定保持当前体态"
    ),
    CONSERVATIVE(
        displayName = "保守减脂",
        weeklyWeightRatio = 0.005f,
        description = "每周约减体重 0.5%，最大程度保护肌肉与体能"
    ),
    STANDARD(
        displayName = "标准减脂",
        weeklyWeightRatio = 0.0075f,
        description = "每周约减体重 0.75%，减脂速率与体感平衡的黄金标准"
    ),
    AGGRESSIVE(
        displayName = "进取减脂",
        weeklyWeightRatio = 0.010f,
        description = "每周约减体重 1.0%，缺口稍大，适合初期或基数较大者"
    )
}

/**
 * 周复盘状态
 */
sealed class WeeklyReviewStatus {
    /** 14 天冷启动保护中 */
    data class ColdStart(
        val daysSinceFirstRecord: Int,
        val requiredDays: Int = 14
    ) : WeeklyReviewStatus()

    /** 过去 7 天数据不足（至少 5 天饮食且 3 天体重） */
    data class InsufficientData(
        val validDietDays: Int,
        val requiredDietDays: Int = 5,
        val validWeightDays: Int,
        val requiredWeightDays: Int = 3
    ) : WeeklyReviewStatus()

    /** 复盘就绪，已生成完整报告 */
    data class Ready(
        val report: WeeklyReviewReport
    ) : WeeklyReviewStatus()
}

/**
 * 周级复盘与自适应 TDEE 校准报告
 */
data class WeeklyReviewReport(
    val startDate: String,
    val endDate: String,
    val validDietDays: Int,
    val validWeightDays: Int,
    val avgDailyIntake: Int,
    val startTrendWeight: Float,
    val endTrendWeight: Float,
    val trendDeltaKg: Float,
    val targetWeeklyLossKg: Float,
    val estimatedTDEE: Int,
    val baselineMaintenanceCalories: Int,
    val currentTargetCalories: Int,
    val suggestedTargetCalories: Int,
    val calorieAdjustment: Int, // 建议调整幅度，如 -100 或 +50 kcal
    val rate: FatLossRate,
    val explanation: String,
    val safetyFloor: Int,
    val isAtSafetyFloor: Boolean,
    val isPlateauAtFloor: Boolean,
    val plateauAdvice: String? = null,
    val waistDelta: Float? = null,
    val bodyFatDelta: Float? = null
)
