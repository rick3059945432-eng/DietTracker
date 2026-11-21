package com.diettracker.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.diettracker.app.model.ApiConfig
import com.diettracker.app.model.AppearanceConfig
import com.diettracker.app.model.DietGoalMode
import com.diettracker.app.model.FatLossRate
import com.diettracker.app.model.GoalConfig
import com.diettracker.app.model.UserProfile
import com.diettracker.app.util.ImageStorageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("diet_tracker_prefs", Context.MODE_PRIVATE)

    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _apiConfig = MutableStateFlow(loadApiConfig())
    val apiConfig: StateFlow<ApiConfig> = _apiConfig.asStateFlow()

    private val _appearanceConfig = MutableStateFlow(loadAppearanceConfig())
    val appearanceConfig: StateFlow<AppearanceConfig> = _appearanceConfig.asStateFlow()

    private val _goalConfig = MutableStateFlow(loadGoalConfig())
    val goalConfig: StateFlow<GoalConfig> = _goalConfig.asStateFlow()

    private val _fatLossRate = MutableStateFlow(loadFatLossRate())
    val fatLossRate: StateFlow<FatLossRate> = _fatLossRate.asStateFlow()

    fun loadUserProfile(): UserProfile {
        return UserProfile(
            nickname = prefs.getString("nickname", "自律伙伴") ?: "自律伙伴",
            avatarUri = ImageStorageUtils.validateSavedUri(prefs.getString("avatarUri", null)),
            gender = prefs.getString("gender", "男") ?: "男",
            age = prefs.getInt("age", 25),
            height = prefs.getFloat("height", 175f),
            weight = prefs.getFloat("weight", 68f)
        )
    }

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit()
            .putString("nickname", profile.nickname)
            .putString("avatarUri", profile.avatarUri)
            .putString("gender", profile.gender)
            .putInt("age", profile.age)
            .putFloat("height", profile.height)
            .putFloat("weight", profile.weight)
            .apply()
        _userProfile.value = profile
    }

    fun loadApiConfig(): ApiConfig {
        return ApiConfig(
            baseUrl = prefs.getString("baseUrl", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            apiKey = prefs.getString("apiKey", "") ?: "",
            chatModel = prefs.getString("chatModel", "gpt-4o-mini") ?: "gpt-4o-mini",
            visionModel = prefs.getString("visionModel", "gpt-4o") ?: "gpt-4o"
        )
    }

    fun saveApiConfig(config: ApiConfig) {
        prefs.edit()
            .putString("baseUrl", config.baseUrl.trimEnd('/'))
            .putString("apiKey", config.apiKey.trim())
            .putString("chatModel", config.chatModel.trim())
            .putString("visionModel", config.visionModel.trim())
            .apply()
        _apiConfig.value = config
    }

    fun loadAppearanceConfig(): AppearanceConfig {
        return AppearanceConfig(
            isDarkMode = prefs.getBoolean("isDarkMode", true),
            customBgUri = ImageStorageUtils.validateSavedUri(prefs.getString("customBgUri", null)),
            bgOpacity = prefs.getFloat("bgOpacity", 0.35f)
        )
    }

    fun saveAppearanceConfig(config: AppearanceConfig) {
        prefs.edit()
            .putBoolean("isDarkMode", config.isDarkMode)
            .putString("customBgUri", config.customBgUri)
            .putFloat("bgOpacity", config.bgOpacity)
            .apply()
        _appearanceConfig.value = config
    }

    fun loadGoalConfig(): GoalConfig {
        val modeName = prefs.getString("goalMode", DietGoalMode.MAINTENANCE.name)
        val mode = try {
            DietGoalMode.valueOf(modeName ?: DietGoalMode.MAINTENANCE.name)
        } catch (e: Exception) {
            DietGoalMode.MAINTENANCE
        }
        return GoalConfig(
            mode = mode,
            customCalories = prefs.getInt("customGoalCalories", 2000),
            customProtein = prefs.getFloat("customGoalProtein", 150f),
            customFat = prefs.getFloat("customGoalFat", 55f),
            customCarbs = prefs.getFloat("customGoalCarbs", 200f),
            customCaloriesMin = prefs.getInt("customGoalCaloriesMin", 1500),
            customProteinMin = prefs.getFloat("customGoalProteinMin", 100f),
            customFatMin = prefs.getFloat("customGoalFatMin", 40f),
            customCarbsMin = prefs.getFloat("customGoalCarbsMin", 120f)
        )
    }

    fun saveGoalConfig(config: GoalConfig) {
        prefs.edit()
            .putString("goalMode", config.mode.name)
            .putInt("customGoalCalories", config.customCalories)
            .putFloat("customGoalProtein", config.customProtein)
            .putFloat("customGoalFat", config.customFat)
            .putFloat("customGoalCarbs", config.customCarbs)
            .putInt("customGoalCaloriesMin", config.customCaloriesMin)
            .putFloat("customGoalProteinMin", config.customProteinMin)
            .putFloat("customGoalFatMin", config.customFatMin)
            .putFloat("customGoalCarbsMin", config.customCarbsMin)
            .apply()
        _goalConfig.value = config
    }

    fun loadFatLossRate(): FatLossRate {
        val name = prefs.getString("fatLossRate", FatLossRate.STANDARD.name)
        return try {
            FatLossRate.valueOf(name ?: FatLossRate.STANDARD.name)
        } catch (_: Exception) {
            FatLossRate.STANDARD
        }
    }

    fun saveFatLossRate(rate: FatLossRate) {
        prefs.edit().putString("fatLossRate", rate.name).apply()
        _fatLossRate.value = rate
    }

    /**
     * 采纳自适应微调目标：按 30%蛋 / 25%脂 / 45%碳 黄金比例同步更新克数并存为自定义模式
     */
    fun applyCalorieAdjustment(newCaloriesTarget: Int) {
        val current = _goalConfig.value
        val pTarget = (newCaloriesTarget * 0.30f / 4f)
        val fTarget = (newCaloriesTarget * 0.25f / 9f)
        val cTarget = (newCaloriesTarget * 0.45f / 4f)

        val minCal = (newCaloriesTarget * 0.82f).toInt().coerceAtLeast(800)
        val minP = (pTarget * 0.78f).coerceAtLeast(1f)
        val minF = (fTarget * 0.75f).coerceAtLeast(1f)
        val minC = (cTarget * 0.75f).coerceAtLeast(1f)

        val updatedConfig = current.copy(
            mode = DietGoalMode.CUSTOM,
            customCalories = newCaloriesTarget,
            customProtein = pTarget,
            customFat = fTarget,
            customCarbs = cTarget,
            customCaloriesMin = minCal,
            customProteinMin = minP,
            customFatMin = minF,
            customCarbsMin = minC
        )
        saveGoalConfig(updatedConfig)
    }

    fun hasPromptedDayTypeToday(todayStr: String): Boolean {
        return prefs.getString("lastDayTypePromptDate", null) == todayStr
    }

    fun markDayTypePromptedToday(todayStr: String) {
        prefs.edit().putString("lastDayTypePromptDate", todayStr).apply()
    }

    // 经典鼓励与自律名句库（无需联网亦可随时轮换）
    val motivationalQuotes = listOf(
        "自律是解决人生所有问题的首要工具。",
        "你今天吃的每一口健康食物，都是给未来身体的投资。",
        "伟大始于微小的自律，坚持造就非凡。",
        "身体是灵魂唯一的圣殿，用心雕琢它。",
        "不要让一时的口腹之欲，耽误了长久的健康体魄。",
        "自律的顶端是自由，掌控饮食即掌控生活。",
        "种一棵树最好的时间是十年前，其次是现在。"
    )

    fun getDailyQuote(nickname: String): String {
        val index = (System.currentTimeMillis() / (1000 * 60 * 60 * 24) % motivationalQuotes.size).toInt()
        val baseQuote = motivationalQuotes[index]
        return "“$baseQuote” —— $nickname，今天坚持了吗？"
    }
}
