package com.diettracker.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.diettracker.app.model.ApiConfig
import com.diettracker.app.model.AppearanceConfig
import com.diettracker.app.model.DietGoalMode
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
            customCarbs = prefs.getFloat("customGoalCarbs", 200f)
        )
    }

    fun saveGoalConfig(config: GoalConfig) {
        prefs.edit()
            .putString("goalMode", config.mode.name)
            .putInt("customGoalCalories", config.customCalories)
            .putFloat("customGoalProtein", config.customProtein)
            .putFloat("customGoalFat", config.customFat)
            .putFloat("customGoalCarbs", config.customCarbs)
            .apply()
        _goalConfig.value = config
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
