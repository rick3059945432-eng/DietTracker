package com.diettracker.app.data.local

import com.diettracker.app.data.preferences.UserPreferencesManager
import com.diettracker.app.model.CustomFood
import com.diettracker.app.model.DailySummary
import com.diettracker.app.model.FatLossRate
import com.diettracker.app.model.GoalConfig
import com.diettracker.app.model.MealRecord
import com.diettracker.app.model.UserProfile
import com.diettracker.app.model.WeightRecord
import com.diettracker.app.model.WikiArticle
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 完整备份数据包
 */
data class BackupMetadata(
    val appName: String = "DietTracker",
    val schemaVersion: Int = 1,
    val appVersion: String = "2.7",
    val exportTimestamp: Long = System.currentTimeMillis(),
    val exportTimeFormatted: String = ""
)

data class DietBackupData(
    val metadata: BackupMetadata,
    val profile: UserProfile,
    val goalConfig: GoalConfig,
    val fatLossRate: FatLossRate,
    val meals: List<MealRecord> = emptyList(),
    val dailySummaries: List<DailySummary> = emptyList(),
    val weights: List<WeightRecord> = emptyList(),
    val customFoods: List<CustomFood> = emptyList(),
    val userWikiArticles: List<WikiArticle> = emptyList()
)

data class ImportResult(
    val isSuccess: Boolean,
    val mealsCount: Int = 0,
    val weightsCount: Int = 0,
    val foodsCount: Int = 0,
    val summariesCount: Int = 0,
    val articlesCount: Int = 0,
    val message: String
)

object BackupDataUtils {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 将全部本地核心数据序列化为格式化 JSON 字符串
     */
    suspend fun exportBackupJson(
        db: DietDatabase,
        prefsManager: UserPreferencesManager
    ): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = System.currentTimeMillis()

        val allMeals = db.mealDao().getAllMealsSync()
        val allSummaries = db.dailySummaryDao().getAllDailySummariesSync()
        val allWeights = db.weightDao().getAllWeightRecordsSync()
        val allCustomFoods = db.customFoodDao().getAllCustomFoodsSync()
        val userWikis = db.wikiDao().getAllArticlesSync().filter { !it.isBuiltin }

        val backup = DietBackupData(
            metadata = BackupMetadata(
                appName = "DietTracker",
                schemaVersion = 1,
                appVersion = "2.7",
                exportTimestamp = now,
                exportTimeFormatted = dateFormat.format(Date(now))
            ),
            profile = prefsManager.userProfile.value,
            goalConfig = prefsManager.goalConfig.value,
            fatLossRate = prefsManager.fatLossRate.value,
            meals = allMeals,
            dailySummaries = allSummaries,
            weights = allWeights,
            customFoods = allCustomFoods,
            userWikiArticles = userWikis
        )

        gson.toJson(backup)
    }

    /**
     * 校验并导入 JSON 备份数据
     */
    suspend fun importBackupJson(
        jsonString: String,
        db: DietDatabase,
        prefsManager: UserPreferencesManager
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            if (jsonString.isBlank()) {
                return@withContext ImportResult(false, message = "备份文件内容为空")
            }

            // 基础合法性校验
            val jsonElement = JsonParser.parseString(jsonString)
            if (!jsonElement.isJsonObject) {
                return@withContext ImportResult(false, message = "文件内容并非有效的 JSON 对象")
            }
            val rootObj = jsonElement.asJsonObject
            if (!rootObj.has("metadata") && !rootObj.has("meals") && !rootObj.has("weights")) {
                return@withContext ImportResult(false, message = "未识别到 DietTracker 的备份特征字段")
            }

            val backup = gson.fromJson(jsonString, DietBackupData::class.java)
                ?: return@withContext ImportResult(false, message = "解析备份数据失败")

            // 1. 恢复个人生理档案与目标配置
            prefsManager.saveUserProfile(backup.profile)
            prefsManager.saveGoalConfig(backup.goalConfig)
            prefsManager.saveFatLossRate(backup.fatLossRate)

            var importedMeals = 0
            var importedWeights = 0
            var importedFoods = 0
            var importedSummaries = 0
            var importedWikis = 0

            // 2. 事务批量入库
            val writableDb = db.writableDatabase
            writableDb.beginTransaction()
            try {
                // 恢复饮食记录
                backup.meals.forEach { meal ->
                    db.mealDao().insertMeal(meal)
                    importedMeals++
                }

                // 恢复每日打卡与大小天
                backup.dailySummaries.forEach { summary ->
                    db.dailySummaryDao().insertOrUpdate(summary)
                    importedSummaries++
                }

                // 恢复体重记录
                backup.weights.forEach { weight ->
                    db.weightDao().insertOrUpdate(weight)
                    importedWeights++
                }

                // 恢复自定义食物库
                backup.customFoods.forEach { food ->
                    db.customFoodDao().insertCustomFood(food)
                    importedFoods++
                }

                // 恢复用户手写小百科词条
                backup.userWikiArticles.forEach { wiki ->
                    db.wikiDao().insertArticle(wiki.copy(isBuiltin = false))
                    importedWikis++
                }

                writableDb.setTransactionSuccessful()
            } finally {
                writableDb.endTransaction()
            }

            val msg = buildString {
                append("成功导入备份！\n")
                append("• 饮食记录：${importedMeals} 条\n")
                append("• 体重记录：${importedWeights} 条\n")
                append("• 每日计划打卡：${importedSummaries} 天\n")
                append("• 自定义食物：${importedFoods} 种\n")
                if (importedWikis > 0) append("• 自定义笔记：${importedWikis} 篇\n")
                append("• 生理档案与目标配置已恢复")
            }

            ImportResult(
                isSuccess = true,
                mealsCount = importedMeals,
                weightsCount = importedWeights,
                foodsCount = importedFoods,
                summariesCount = importedSummaries,
                articlesCount = importedWikis,
                message = msg
            )
        } catch (e: Exception) {
            ImportResult(
                isSuccess = false,
                message = "导入失败：${e.localizedMessage ?: e.message ?: "未知格式错误"}"
            )
        }
    }
}
