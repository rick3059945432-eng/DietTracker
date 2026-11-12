package com.diettracker.app.model

enum class AppPage(val title: String) {
    DIET_DASHBOARD("今日饮食"),
    AI_COACH("AI私教"),
    FITNESS_WIKI("小百科")
    // 预留后续继续扩展新活页位置
}

data class WikiArticle(
    val id: Long = 0,
    val title: String,
    val category: String,
    val content: String,
    val isBuiltin: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

val WIKI_CATEGORIES = listOf("饮食营养", "减脂避坑", "训练干货", "补剂知识", "我的笔记")

data class ChatMessage(
    val id: Long = 0,
    val date: String, // Format: YYYY-MM-DD
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MealType(val displayName: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐")
}

data class MealRecord(
    val id: Long = 0,
    val date: String, // Format: YYYY-MM-DD
    val mealType: MealType,
    val foodName: String,
    val calories: Int,
    val protein: Float, // grams
    val fat: Float,     // grams
    val carbs: Float,   // grams
    val note: String = "",
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class DailySummary(
    val date: String, // Format: YYYY-MM-DD
    val burnedCalories: Int = 0, // 手动填写的运动消耗
    val aiAdvice: String = "",
    val aiAdviceTimestamp: Long = 0,
    val goalMode: String? = null, // 该日独立存储的营养模式
    val customCalories: Int? = null,
    val customProtein: Float? = null,
    val customFat: Float? = null,
    val customCarbs: Float? = null,
    val dayType: String? = null // TRAINING / REST / CHEAT
) {
    fun toGoalConfig(fallback: GoalConfig): GoalConfig {
        val parsedMode = goalMode?.let {
            try { DietGoalMode.valueOf(it) } catch (_: Exception) { null }
        } ?: return fallback
        return GoalConfig(
            mode = parsedMode,
            customCalories = customCalories ?: fallback.customCalories,
            customProtein = customProtein ?: fallback.customProtein,
            customFat = customFat ?: fallback.customFat,
            customCarbs = customCarbs ?: fallback.customCarbs
        )
    }

    fun parsedDayType(): DayType? = dayType?.let {
        try { DayType.valueOf(it) } catch (_: Exception) { null }
    }
}

data class UserProfile(
    val nickname: String = "自律伙伴",
    val avatarUri: String? = null,
    val gender: String = "男", // "男" or "女"
    val age: Int = 25,
    val height: Float = 175f, // cm
    val weight: Float = 68f   // kg
) {
    // BMI = 体重(kg) / (身高(m))^2
    val bmi: Float
        get() {
            val heightInMeters = height / 100f
            return if (heightInMeters > 0) weight / (heightInMeters * heightInMeters) else 0f
        }

    // Mifflin-St Jeor 公式计算 BMR
    val bmr: Int
        get() {
            return if (gender == "男") {
                (10 * weight + 6.25f * height - 5 * age + 5).toInt()
            } else {
                (10 * weight + 6.25f * height - 5 * age - 161).toInt()
            }
        }

    // 维持基础消耗预估 (以久坐低活动系数 1.2 为基准)
    val maintenanceCalories: Int
        get() = (bmr * 1.2f).toInt()
}

data class ApiConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val chatModel: String = "gpt-4o-mini",
    val visionModel: String = "gpt-4o"
)

data class AppearanceConfig(
    val isDarkMode: Boolean = true,
    val customBgUri: String? = null,
    val bgOpacity: Float = 0.35f // 0f (纯色背景) 到 1f (原图无遮罩)
)

data class TodayNutritionSummary(
    val totalCalories: Int,
    val totalProtein: Float,
    val totalFat: Float,
    val totalCarbs: Float,
    val burnedCalories: Int,
    val targetCalories: Int
)

data class FoodNutritionEstimate(
    val foodName: String,
    val calories: Int,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val explanation: String = ""
)

enum class FoodUnitType(
    val displayName: String,
    val unitSuffix: String,
    val baseAmount: Float
) {
    PER_100G("每100g", "g", 100f),
    PER_100ML("每100ml", "ml", 100f),
    PER_PIECE("每1个", "个", 1f),
    PER_SERVING("每1份", "份", 1f)
}

data class CustomFood(
    val id: Long = 0,
    val name: String,
    val unitType: FoodUnitType = FoodUnitType.PER_100G,
    val calories: Int,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class CustomFoodEstimate(
    val foodName: String,
    val unitType: FoodUnitType,
    val calories: Int,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val explanation: String = ""
)

enum class DietGoalMode(
    val displayName: String,
    val shortBadge: String,
    val calorieOffset: Int,
    val proteinRatio: Float,
    val fatRatio: Float,
    val carbsRatio: Float,
    val description: String
) {
    MAINTENANCE(
        displayName = "健康维持",
        shortBadge = "维持模式",
        calorieOffset = 0,
        proteinRatio = 0.20f,
        fatRatio = 0.28f,
        carbsRatio = 0.52f,
        description = "收支平衡，均衡营养（20%蛋 / 28%脂 / 52%碳）"
    ),
    FAT_LOSS(
        displayName = "减脂塑形",
        shortBadge = "减脂模式",
        calorieOffset = -400,
        proteinRatio = 0.32f,
        fatRatio = 0.25f,
        carbsRatio = 0.43f,
        description = "温和缺口 -400 kcal，高蛋白护肌控碳（32%蛋 / 25%脂 / 43%碳）"
    ),
    MUSCLE_GAIN(
        displayName = "健身增肌",
        shortBadge = "增肌模式",
        calorieOffset = 300,
        proteinRatio = 0.28f,
        fatRatio = 0.22f,
        carbsRatio = 0.50f,
        description = "温和盈余 +300 kcal，高蛋白高碳充能（28%蛋 / 22%脂 / 50%碳）"
    ),
    CUSTOM(
        displayName = "自定义上限",
        shortBadge = "自定义",
        calorieOffset = 0,
        proteinRatio = 0f,
        fatRatio = 0f,
        carbsRatio = 0f,
        description = "按你自己手动设定的热量和蛋/脂/碳克数上限执行"
    )
}

enum class DayType(
    val displayName: String,
    val shortLabel: String,
    val badgeEmoji: String,
    val summaryTip: String
) {
    TRAINING(
        displayName = "训练日（大天）",
        shortLabel = "训练日",
        badgeEmoji = "🏋️",
        summaryTip = "高强度训练会大量消耗肌糖原：蛋白质维持高水平，适当压低脂肪，把更多热量和碳水集中用于支撑训练表现和练后肌糖原恢复。"
    ),
    REST(
        displayName = "休息日（小天）",
        shortLabel = "休息日",
        badgeEmoji = "🛋️",
        summaryTip = "非训练日能量与糖原需求低：主食碳水明显收紧，蛋白质依然保持高标准防止肌肉分解，脂肪维持基础保底以保证激素健康和饱腹感。"
    ),
    CHEAT(
        displayName = "放纵日（回补/放纵餐）",
        shortLabel = "放纵日",
        badgeEmoji = "🎉",
        summaryTip = "心理放松与代谢回补：热量放宽到维持消耗附近或略高。记得先吃够蛋白质打底，尽量选高碳中低脂美食（如日料、牛排、清汤牛肉），少碰高油高糖混合炸弹！"
    )
}

data class DateCalendarInfo(
    val goalMode: DietGoalMode,
    val dayType: DayType? = null
)

data class GoalConfig(
    val mode: DietGoalMode = DietGoalMode.MAINTENANCE,
    val customCalories: Int = 2000,
    val customProtein: Float = 150f,
    val customFat: Float = 55f,
    val customCarbs: Float = 200f
)

data class DailyNutritionTargets(
    val mode: DietGoalMode,
    val dayType: DayType?,
    val caloriesTarget: Int,
    val proteinTarget: Float,
    val fatTarget: Float,
    val carbsTarget: Float
)

private data class DayPlanRule(
    val calorieOffset: Int,
    val proteinRatio: Float,
    val fatRatio: Float,
    val carbsRatio: Float,
    val planDetail: String
)

private fun resolveDayPlanRule(mode: DietGoalMode, dayType: DayType): DayPlanRule {
    return when (mode) {
        DietGoalMode.FAT_LOSS -> when (dayType) {
            DayType.TRAINING -> DayPlanRule(
                calorieOffset = -150,
                proteinRatio = 0.30f,
                fatRatio = 0.20f,
                carbsRatio = 0.50f,
                planDetail = "减脂×训练日：热量略收 -150 kcal，提高碳水至 50% 供能举铁、压低脂肪至 20%、蛋白 30% 护肌。"
            )
            DayType.REST -> DayPlanRule(
                calorieOffset = -500,
                proteinRatio = 0.35f,
                fatRatio = 0.30f,
                carbsRatio = 0.35f,
                planDetail = "减脂×休息日：加大缺口至 -500 kcal，砍低碳水至 35%，蛋白质拉到 35% 死守肌肉并提升饱腹感。"
            )
            DayType.CHEAT -> DayPlanRule(
                calorieOffset = 200,
                proteinRatio = 0.25f,
                fatRatio = 0.25f,
                carbsRatio = 0.50f,
                planDetail = "减脂×放纵日：热量放宽至维持 +200 kcal 唤醒代谢，高碳 50% 回补糖原、蛋白 25% 保底，控制脂肪不过 25%。"
            )
        }
        DietGoalMode.MUSCLE_GAIN -> when (dayType) {
            DayType.TRAINING -> DayPlanRule(
                calorieOffset = 400,
                proteinRatio = 0.25f,
                fatRatio = 0.20f,
                carbsRatio = 0.55f,
                planDetail = "增肌×训练日：热量盈余 +400 kcal，55% 高碳水拉满训练容量，25% 蛋白促合成。"
            )
            DayType.REST -> DayPlanRule(
                calorieOffset = 150,
                proteinRatio = 0.28f,
                fatRatio = 0.27f,
                carbsRatio = 0.45f,
                planDetail = "增肌×休息日：微盈余 +150 kcal 干净修复（少长肥肉），蛋白升至 28% 支撑全天肌肉合成。"
            )
            DayType.CHEAT -> DayPlanRule(
                calorieOffset = 500,
                proteinRatio = 0.22f,
                fatRatio = 0.28f,
                carbsRatio = 0.50f,
                planDetail = "增肌×放纵日：盈余放宽至 +500 kcal 开心加餐，留意别摄入过多劣质反式脂肪即可。"
            )
        }
        DietGoalMode.MAINTENANCE, DietGoalMode.CUSTOM -> when (dayType) {
            DayType.TRAINING -> DayPlanRule(
                calorieOffset = 200,
                proteinRatio = 0.22f,
                fatRatio = 0.23f,
                carbsRatio = 0.55f,
                planDetail = "维持×训练日：热量 +200 kcal 用干净碳水（55%）补足训练消耗。"
            )
            DayType.REST -> DayPlanRule(
                calorieOffset = -100,
                proteinRatio = 0.24f,
                fatRatio = 0.30f,
                carbsRatio = 0.46f,
                planDetail = "维持×休息日：略收 -100 kcal 碳水，与训练日平衡保持一周体重稳定。"
            )
            DayType.CHEAT -> DayPlanRule(
                calorieOffset = 300,
                proteinRatio = 0.20f,
                fatRatio = 0.28f,
                carbsRatio = 0.52f,
                planDetail = "维持×放纵日：放宽至 +300 kcal 享受一顿美食，蛋白仍占 20% 打底。"
            )
        }
    }
}

fun describeDayPlan(mode: DietGoalMode, dayType: DayType): String {
    if (mode == DietGoalMode.CUSTOM) {
        return "当前处于【自定义上限】模式：热量与碳蛋脂将继续严格按你手动设定的克数执行，同时在日历中为你标记「${dayType.shortLabel}」。"
    }
    return resolveDayPlanRule(mode, dayType).planDetail
}

fun calculateDailyNutritionTargets(
    profile: UserProfile,
    burnedCalories: Int,
    goalConfig: GoalConfig,
    dayType: DayType? = null
): DailyNutritionTargets {
    val mode = goalConfig.mode
    if (mode == DietGoalMode.CUSTOM) {
        return DailyNutritionTargets(
            mode = mode,
            dayType = dayType,
            caloriesTarget = goalConfig.customCalories.coerceAtLeast(1),
            proteinTarget = goalConfig.customProtein.coerceAtLeast(1f),
            fatTarget = goalConfig.customFat.coerceAtLeast(1f),
            carbsTarget = goalConfig.customCarbs.coerceAtLeast(1f)
        )
    }

    val rule = if (dayType != null) {
        resolveDayPlanRule(mode, dayType)
    } else {
        DayPlanRule(
            calorieOffset = mode.calorieOffset,
            proteinRatio = mode.proteinRatio,
            fatRatio = mode.fatRatio,
            carbsRatio = mode.carbsRatio,
            planDetail = mode.description
        )
    }

    val totalTargetCalories = (profile.maintenanceCalories + burnedCalories + rule.calorieOffset)
        .coerceAtLeast(800)

    return DailyNutritionTargets(
        mode = mode,
        dayType = dayType,
        caloriesTarget = totalTargetCalories,
        proteinTarget = (totalTargetCalories * rule.proteinRatio / 4f),
        fatTarget = (totalTargetCalories * rule.fatRatio / 9f),
        carbsTarget = (totalTargetCalories * rule.carbsRatio / 4f)
    )
}
