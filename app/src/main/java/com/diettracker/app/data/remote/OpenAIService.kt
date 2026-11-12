package com.diettracker.app.data.remote

import com.diettracker.app.model.ApiConfig
import com.diettracker.app.model.ChatMessage
import com.diettracker.app.model.CustomFoodEstimate
import com.diettracker.app.model.DailyNutritionTargets
import com.diettracker.app.model.FoodNutritionEstimate
import com.diettracker.app.model.FoodUnitType
import com.diettracker.app.model.MealRecord
import com.diettracker.app.model.UserProfile
import com.diettracker.app.model.WIKI_CATEGORIES
import com.diettracker.app.model.WikiArticle
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAIService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // 1. 文字描述估算热量
    suspend fun estimateFoodNutrition(
        textDescription: String,
        config: ApiConfig
    ): Result<FoodNutritionEstimate> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先在设置中填写 API Key！"))
            }

            val prompt = """
                你是一位专业营养师。请根据用户输入的食物描述估算其总热量及三大营养素。
                用户食物描述："$textDescription"
                
                请必须且仅输出标准的 JSON 格式（不要添加 markdown 代码块标签或任何额外文字）：
                {
                    "foodName": "食物名称简述",
                    "calories": 整数千卡数值,
                    "protein": 浮点数蛋白质克数,
                    "fat": 浮点数脂肪克数,
                    "carbs": 浮点数碳水克数,
                    "explanation": "简短分析说明（50字内）"
                }
            """.trimIndent()

            val requestBody = JsonObject().apply {
                addProperty("model", config.chatModel)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", "你是一个专业营养计算助手，仅严格返回要求的 JSON 数据。")
                    })
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", prompt)
                    })
                })
                addProperty("temperature", 0.3)
            }

            val resultJson = executeRequest(config, requestBody.toString())
            val parsed = parseNutritionJson(resultJson)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. 拍照/传图识图计算热量 (多模态 Vision)
    suspend fun analyzeFoodImage(
        base64Image: String,
        mimeType: String = "image/jpeg",
        config: ApiConfig
    ): Result<FoodNutritionEstimate> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                你是一位专业营养师。请仔细观察图片中的食物，辨认食物种类、份量，并估算其总热量与三大营养素（蛋白质、脂肪、碳水）。
                
                请必须且仅返回标准 JSON（不要包含任何 markdown 代码块或额外说明）：
                {
                    "foodName": "识别出的主要菜品或食物名称",
                    "calories": 整数千卡数值,
                    "protein": 浮点数蛋白质克数,
                    "fat": 浮点数脂肪克数,
                    "carbs": 浮点数碳水克数,
                    "explanation": "食材构成与估算说明（50字内）"
                }
            """.trimIndent()

            val resultJson = executeVisionRequest(prompt, base64Image, mimeType, config)
            Result.success(parseNutritionJson(resultJson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2b. 拍摄/上传营养成分表或配料表，提取基准营养数据（按每100g/100ml/1个/1份，并自动换算 kJ -> kcal）
    suspend fun analyzeNutritionLabelImage(
        base64Image: String,
        mimeType: String = "image/jpeg",
        config: ApiConfig
    ): Result<CustomFoodEstimate> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                你是一位食品营养成分表识别助手。用户拍摄了一张食品包装背面的“营养成分表”或配料表。
                请读取表格中的数值，返回以下标准 JSON（不要添加 markdown 代码块标签或任何额外文字）：
                {
                    "foodName": "食品名称（图中能看到品牌或品名请写出，看不到请写“包装食品”）",
                    "unitType": "PER_100G" 或 "PER_100ML" 或 "PER_PIECE" 或 "PER_SERVING",
                    "calories": 整数千卡数值(kcal),
                    "protein": 浮点数蛋白质克数,
                    "fat": 浮点数脂肪克数,
                    "carbs": 浮点数碳水化合物克数,
                    "explanation": "识别说明（例如：已将原表能量 xxx kJ 按 ÷4.184 换算为 kcal，基准每100g）"
                }

                重要规则：
                1. 如果成分表能量单位是千焦（kJ），必须除以 4.184 并四舍五入换算为千卡（kcal）整数！
                2. 如果表头是“每100g”或“每100克”，unitType 填 "PER_100G"，直接提取每100g的数值，不要乘以整袋重量。
                3. 如果表头是“每100ml”或“每100毫升”，unitType 填 "PER_100ML"，直接提取每100ml的数值。
                4. 如果表头按“每份”或“每个/每袋/每支”标注，unitType 填 "PER_SERVING" 或 "PER_PIECE"。
            """.trimIndent()

            val resultJson = executeVisionRequest(prompt, base64Image, mimeType, config)
            Result.success(parseCustomFoodJson(resultJson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeVisionRequest(
        prompt: String,
        base64Image: String,
        mimeType: String,
        config: ApiConfig
    ): String {
        if (config.apiKey.isBlank()) {
            throw Exception("请先在设置中填写 API Key！")
        }

        val contentArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", prompt)
            })
            add(JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", JsonObject().apply {
                    addProperty("url", "data:$mimeType;base64,$base64Image")
                })
            })
        }

        val requestBody = JsonObject().apply {
            addProperty("model", config.visionModel)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", contentArray)
                })
            })
            addProperty("max_tokens", 800)
            addProperty("temperature", 0.2)
        }

        return executeRequest(config, requestBody.toString())
    }

    // 3. AI 私教多轮对话（实时注入当天饮食、目标模式、大小天状态和历史上下文）
    suspend fun chatWithCoach(
        dateStr: String,
        profile: UserProfile,
        meals: List<MealRecord>,
        burnedCalories: Int,
        targets: DailyNutritionTargets,
        historyMessages: List<ChatMessage>,
        userMessage: String,
        config: ApiConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先在设置中填写 API Key！"))
            }

            val totalCal = meals.sumOf { it.calories }
            val totalP = meals.map { it.protein }.sum()
            val totalF = meals.map { it.fat }.sum()
            val totalC = meals.map { it.carbs }.sum()
            val mealsText = if (meals.isEmpty()) {
                "（暂无餐食记录）"
            } else {
                buildString {
                    meals.forEach { meal ->
                        append("- 【${meal.mealType.displayName}】${meal.foodName}: ${meal.calories} kcal (蛋白 ${meal.protein}g, 脂肪 ${meal.fat}g, 碳水 ${meal.carbs}g)\n")
                    }
                }
            }

            val remainingCal = targets.caloriesTarget - totalCal
            val systemContext = """
                你是一位亲切、专业的私人健身与营养教练。你会结合用户的真实生理数据、当天饮食进度以及历史聊天记录，为用户提供实用、科学的指导和替代方案。

                【当前日期：$dateStr】
                【用户基本信息】
                - 昵称：${profile.nickname}
                - 性别：${profile.gender}，年龄：${profile.age}岁，身高：${profile.height}cm，体重：${profile.weight}kg
                - BMI：${String.format("%.1f", profile.bmi)}，BMR：${profile.bmr} kcal，基础维持 TDEE：${profile.maintenanceCalories} kcal
                - 今日运动额外消耗：$burnedCalories kcal

                【当前执行的饮食目标模式与大小天】
                - 主模式：${targets.mode.displayName}（${targets.mode.description}）
                - 日类型：${targets.dayType?.displayName ?: "未单独设定训练/休息日"}
                - 目标热量上限：${targets.caloriesTarget} kcal（当前还剩：$remainingCal kcal）
                - 三大营养素上限：蛋白质 ${String.format("%.1f", targets.proteinTarget)}g，脂肪 ${String.format("%.1f", targets.fatTarget)}g，碳水 ${String.format("%.1f", targets.carbsTarget)}g

                【今日已摄入饮食实时明细】
                $mealsText
                - 当前总摄入：$totalCal kcal
                - 当前三大营养素：蛋白质 ${String.format("%.1f", totalP)}g，脂肪 ${String.format("%.1f", totalF)}g，碳水 ${String.format("%.1f", totalC)}g

                【回复要求】
                请必须使用干净的纯文本输出，严禁使用任何 Markdown 标记符号（禁止出现 **、*、#、##、###、`、>、--- 等符号）。分段和分条请直接用换行、数字序号（1. 2.）或小圆点（•），语言自然像真私教聊天。
            """.trimIndent()

            val messagesArray = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemContext)
                })
                // 保留最近 20 条当日对话作为上下文
                historyMessages.takeLast(20).forEach { msg ->
                    add(JsonObject().apply {
                        addProperty("role", msg.role)
                        addProperty("content", msg.content)
                    })
                }
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userMessage)
                })
            }

            val requestBody = JsonObject().apply {
                addProperty("model", config.chatModel)
                add("messages", messagesArray)
                addProperty("temperature", 0.7)
            }

            val rawResponse = executeRequest(config, requestBody.toString())
            Result.success(stripMarkdown(rawResponse))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 3b. AI 生成健身小百科词条卡片
    suspend fun generateWikiArticle(
        topic: String,
        config: ApiConfig
    ): Result<WikiArticle> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先在设置中填写 API Key！"))
            }

            val prompt = """
                你是一位运动营养学与科学健身百科编辑。请围绕用户想了解的主题：“$topic”，整理一篇通俗、严谨、可直接收藏随时翻看的健身小百科知识卡片。

                请严格返回标准 JSON（不要包含 markdown 代码块或任何额外说明）：
                {
                    "title": "简洁明确的百科标题（20字以内）",
                    "category": "从以下五项中选最合适的一项：饮食营养 / 减脂避坑 / 训练干货 / 补剂知识 / 我的笔记",
                    "content": "结构清晰的正文内容（纯文本，严禁使用 **、##、* 等 Markdown 符号，分点请用 1. 2. 或 • 换行）"
                }
            """.trimIndent()

            val requestBody = JsonObject().apply {
                addProperty("model", config.chatModel)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", "你是一位科学健身百科助手，仅返回严格要求的 JSON 数据，content 内部保持干净纯文本，不使用星号或井号。")
                    })
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", prompt)
                    })
                })
                addProperty("temperature", 0.4)
            }

            val rawJson = executeRequest(config, requestBody.toString())
            val obj = cleanJsonPayload(rawJson)
            val title = stripMarkdown(obj.get("title")?.asString ?: topic.take(20))
            val rawCat = obj.get("category")?.asString?.trim() ?: "我的笔记"
            val category = if (rawCat in WIKI_CATEGORIES) rawCat else "我的笔记"
            val content = stripMarkdown(obj.get("content")?.asString ?: "")

            Result.success(
                WikiArticle(
                    title = title,
                    category = category,
                    content = content,
                    isBuiltin = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. 检索接口下可用的模型列表 (GET /models)
    suspend fun fetchAvailableModels(
        baseUrl: String,
        apiKey: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(Exception("请先填写 API Base URL！"))
            }
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先填写 API Key！"))
            }

            val candidates = buildCandidateUrls(baseUrl, "/models")
            var lastError: Exception? = null

            for ((index, endpoint) in candidates.withIndex()) {
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .get()
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (response.code == 404 && index < candidates.lastIndex) {
                            lastError = Exception("404 Not Found: $endpoint")
                            return@use
                        }
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            throw Exception("获取失败 (${response.code}): $errorBody")
                        }
                        val body = response.body?.string() ?: throw Exception("返回内容为空")
                        val models = parseModelsResponse(body)
                        if (models.isEmpty()) {
                            throw Exception("接口返回成功，但模型列表为空")
                        }
                        return@withContext Result.success(models)
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (index == candidates.lastIndex) {
                        throw e
                    }
                }
            }

            Result.failure(lastError ?: Exception("无法获取模型列表"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseModelsResponse(rawJson: String): List<String> {
        val trimmed = rawJson.trim()
        val ids = mutableListOf<String>()

        if (trimmed.startsWith("[")) {
            val arr = gson.fromJson(trimmed, JsonArray::class.java)
            arr?.forEach { elem ->
                if (elem.isJsonObject) {
                    elem.asJsonObject.get("id")?.asString?.let { ids.add(it) }
                } else if (elem.isJsonPrimitive) {
                    ids.add(elem.asString)
                }
            }
        } else {
            val obj = gson.fromJson(trimmed, JsonObject::class.java)
            val dataArray = obj?.getAsJsonArray("data") ?: obj?.getAsJsonArray("models")
            dataArray?.forEach { elem ->
                if (elem.isJsonObject) {
                    val mObj = elem.asJsonObject
                    val id = mObj.get("id")?.asString ?: mObj.get("name")?.asString
                    if (!id.isNullOrBlank()) {
                        ids.add(id.removePrefix("models/"))
                    }
                } else if (elem.isJsonPrimitive) {
                    ids.add(elem.asString)
                }
            }
        }
        return ids.distinct().sorted()
    }

    private fun buildCandidateUrls(baseUrl: String, path: String): List<String> {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val primary = "$cleanBase$path"
        val hasVersionSuffix = Regex(".*/v\\d+([a-zA-Z0-9_-]*)?$").matches(cleanBase)
        return if (hasVersionSuffix) {
            listOf(primary)
        } else {
            listOf(primary, "$cleanBase/v1$path")
        }
    }

    private fun executeRequest(config: ApiConfig, bodyJson: String): String {
        val candidates = buildCandidateUrls(config.baseUrl, "/chat/completions")
        var lastError: Exception? = null

        for ((index, endpoint) in candidates.withIndex()) {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404 && index < candidates.lastIndex) {
                    lastError = Exception("404 Not Found: $endpoint")
                    return@use
                }
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw Exception("API 响应失败 (${response.code}): $errorBody")
                }
                val responseBody = response.body?.string() ?: throw Exception("返回内容为空")
                val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val firstChoice = choices[0].asJsonObject
                    val message = firstChoice.getAsJsonObject("message")
                    return message.get("content").asString
                } else {
                    throw Exception("API 未返回有效内容: $responseBody")
                }
            }
        }
        throw (lastError ?: Exception("API 请求失败"))
    }

    private fun cleanJsonPayload(rawText: String): JsonObject {
        var clean = rawText.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return gson.fromJson(clean.trim(), JsonObject::class.java)
    }

    private fun parseNutritionJson(rawText: String): FoodNutritionEstimate {
        val obj = cleanJsonPayload(rawText)
        return FoodNutritionEstimate(
            foodName = stripMarkdown(obj.get("foodName")?.asString ?: "自定义食物"),
            calories = obj.get("calories")?.asInt ?: 0,
            protein = obj.get("protein")?.asFloat ?: 0f,
            fat = obj.get("fat")?.asFloat ?: 0f,
            carbs = obj.get("carbs")?.asFloat ?: 0f,
            explanation = stripMarkdown(obj.get("explanation")?.asString ?: "")
        )
    }

    private fun parseCustomFoodJson(rawText: String): CustomFoodEstimate {
        val obj = cleanJsonPayload(rawText)
        val unitStr = obj.get("unitType")?.asString ?: "PER_100G"
        val unitType = try {
            FoodUnitType.valueOf(unitStr)
        } catch (e: Exception) {
            FoodUnitType.PER_100G
        }
        return CustomFoodEstimate(
            foodName = stripMarkdown(obj.get("foodName")?.asString ?: "包装食品"),
            unitType = unitType,
            calories = obj.get("calories")?.asInt ?: 0,
            protein = obj.get("protein")?.asFloat ?: 0f,
            fat = obj.get("fat")?.asFloat ?: 0f,
            carbs = obj.get("carbs")?.asFloat ?: 0f,
            explanation = stripMarkdown(obj.get("explanation")?.asString ?: "")
        )
    }

    companion object {
        /**
         * 兜底清理 AI 回复中可能残留的 Markdown 符号（**、##、*、` 等），确保展示为干净的纯文本。
         */
        fun stripMarkdown(text: String): String {
            return text
                .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
                .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
                .replace(Regex("(?m)^\\s*---+$"), "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("*", "")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        }
    }
}
