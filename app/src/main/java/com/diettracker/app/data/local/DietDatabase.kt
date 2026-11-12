package com.diettracker.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.diettracker.app.model.ChatMessage
import com.diettracker.app.model.CustomFood
import com.diettracker.app.model.DailySummary
import com.diettracker.app.model.DateCalendarInfo
import com.diettracker.app.model.DayType
import com.diettracker.app.model.DietGoalMode
import com.diettracker.app.model.FoodUnitType
import com.diettracker.app.model.MealRecord
import com.diettracker.app.model.MealType
import com.diettracker.app.model.WikiArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DietDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "diet_tracker.db",
    null,
    6
) {
    private val dbChangeNotifier = MutableStateFlow(System.currentTimeMillis())

    private fun notifyDataChanged() {
        dbChangeNotifier.value = System.currentTimeMillis()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE meal_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                mealType TEXT NOT NULL,
                foodName TEXT NOT NULL,
                calories INTEGER NOT NULL,
                protein REAL NOT NULL,
                fat REAL NOT NULL,
                carbs REAL NOT NULL,
                note TEXT,
                imageUri TEXT,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_summaries (
                date TEXT PRIMARY KEY,
                burnedCalories INTEGER NOT NULL,
                aiAdvice TEXT,
                aiAdviceTimestamp INTEGER NOT NULL,
                goalMode TEXT,
                customCalories INTEGER,
                customProtein REAL,
                customFat REAL,
                customCarbs REAL,
                dayType TEXT
            )
            """.trimIndent()
        )

        createCustomFoodsTable(db)
        createChatMessagesTable(db)
        createWikiArticlesTableAndSeed(db)
    }

    private fun createCustomFoodsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_foods (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                unitType TEXT NOT NULL,
                calories INTEGER NOT NULL,
                protein REAL NOT NULL,
                fat REAL NOT NULL,
                carbs REAL NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createChatMessagesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createWikiArticlesTableAndSeed(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS wiki_articles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                content TEXT NOT NULL,
                isBuiltin INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM wiki_articles", null)
        val isEmpty = countCursor.use { c -> if (c.moveToFirst()) c.getInt(0) == 0 else true }
        if (isEmpty) {
            val baseTs = System.currentTimeMillis()
            WikiSeedData.getBuiltinArticles().forEachIndexed { idx, article ->
                val values = ContentValues().apply {
                    put("title", article.title)
                    put("category", article.category)
                    put("content", article.content)
                    put("isBuiltin", 1)
                    put("timestamp", baseTs - idx * 1000L)
                }
                db.insert("wiki_articles", null, values)
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 保留既有饮食记录，仅增量升级新表与新列
        if (oldVersion < 2) {
            createCustomFoodsTable(db)
        }
        if (oldVersion < 3) {
            val alterColumns = listOf(
                "ALTER TABLE daily_summaries ADD COLUMN goalMode TEXT",
                "ALTER TABLE daily_summaries ADD COLUMN customCalories INTEGER",
                "ALTER TABLE daily_summaries ADD COLUMN customProtein REAL",
                "ALTER TABLE daily_summaries ADD COLUMN customFat REAL",
                "ALTER TABLE daily_summaries ADD COLUMN customCarbs REAL"
            )
            alterColumns.forEach { sql ->
                try {
                    db.execSQL(sql)
                } catch (_: Exception) {
                }
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE daily_summaries ADD COLUMN dayType TEXT")
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 5) {
            createChatMessagesTable(db)
        }
        if (oldVersion < 6) {
            createWikiArticlesTableAndSeed(db)
        }
    }

    private fun mealContentValues(
        meal: MealRecord,
        targetDate: String = meal.date,
        targetMealType: MealType = meal.mealType,
        targetTimestamp: Long = meal.timestamp
    ): ContentValues = ContentValues().apply {
        put("date", targetDate)
        put("mealType", targetMealType.name)
        put("foodName", meal.foodName)
        put("calories", meal.calories)
        put("protein", meal.protein)
        put("fat", meal.fat)
        put("carbs", meal.carbs)
        put("note", meal.note)
        put("imageUri", meal.imageUri)
        put("timestamp", targetTimestamp)
    }

    private val _mealDao = object : MealDao {
        override fun getMealsByDate(date: String): Flow<List<MealRecord>> {
            return dbChangeNotifier.map {
                getMealsByDateSync(date)
            }
        }

        override suspend fun insertMeal(meal: MealRecord): Long = withContext(Dispatchers.IO) {
            val id = writableDatabase.insert("meal_records", null, mealContentValues(meal))
            notifyDataChanged()
            id
        }

        override suspend fun updateMeal(meal: MealRecord) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("foodName", meal.foodName)
                put("calories", meal.calories)
                put("protein", meal.protein)
                put("fat", meal.fat)
                put("carbs", meal.carbs)
                put("note", meal.note)
                put("imageUri", meal.imageUri)
            }
            writableDatabase.update("meal_records", values, "id = ?", arrayOf(meal.id.toString()))
            notifyDataChanged()
        }

        override suspend fun deleteMeal(meal: MealRecord) = withContext(Dispatchers.IO) {
            writableDatabase.delete("meal_records", "id = ?", arrayOf(meal.id.toString()))
            notifyDataChanged()
        }

        override suspend fun getMealsByDateSync(date: String): List<MealRecord> = withContext(Dispatchers.IO) {
            val list = mutableListOf<MealRecord>()
            val cursor = readableDatabase.rawQuery(
                "SELECT * FROM meal_records WHERE date = ? ORDER BY timestamp ASC",
                arrayOf(date)
            )
            cursor.use { c ->
                val idIdx = c.getColumnIndex("id")
                val dateIdx = c.getColumnIndex("date")
                val mealTypeIdx = c.getColumnIndex("mealType")
                val foodNameIdx = c.getColumnIndex("foodName")
                val calIdx = c.getColumnIndex("calories")
                val pIdx = c.getColumnIndex("protein")
                val fIdx = c.getColumnIndex("fat")
                val cIdx = c.getColumnIndex("carbs")
                val noteIdx = c.getColumnIndex("note")
                val imgIdx = c.getColumnIndex("imageUri")
                val tsIdx = c.getColumnIndex("timestamp")

                while (c.moveToNext()) {
                    val mType = try {
                        MealType.valueOf(c.getString(mealTypeIdx))
                    } catch (e: Exception) {
                        MealType.BREAKFAST
                    }
                    list.add(
                        MealRecord(
                            id = c.getLong(idIdx),
                            date = c.getString(dateIdx),
                            mealType = mType,
                            foodName = c.getString(foodNameIdx),
                            calories = c.getInt(calIdx),
                            protein = c.getFloat(pIdx),
                            fat = c.getFloat(fIdx),
                            carbs = c.getFloat(cIdx),
                            note = c.getString(noteIdx) ?: "",
                            imageUri = c.getString(imgIdx),
                            timestamp = c.getLong(tsIdx)
                        )
                    )
                }
            }
            list
        }

        override suspend fun replaceMealsForDateAndType(
            date: String,
            mealType: MealType,
            sourceMeals: List<MealRecord>
        ) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete(
                    "meal_records",
                    "date = ? AND mealType = ?",
                    arrayOf(date, mealType.name)
                )
                val baseTs = System.currentTimeMillis()
                sourceMeals.forEachIndexed { idx, src ->
                    val values = mealContentValues(
                        meal = src,
                        targetDate = date,
                        targetMealType = mealType,
                        targetTimestamp = baseTs + idx
                    )
                    db.insert("meal_records", null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyDataChanged()
        }
    }

    private val _dailySummaryDao = object : DailySummaryDao {
        override fun getDailySummary(date: String): Flow<DailySummary?> {
            return dbChangeNotifier.map {
                getDailySummarySync(date)
            }
        }

        override suspend fun getDailySummarySync(date: String): DailySummary? = withContext(Dispatchers.IO) {
            val cursor = readableDatabase.rawQuery(
                "SELECT * FROM daily_summaries WHERE date = ?",
                arrayOf(date)
            )
            cursor.use { c ->
                if (c.moveToNext()) {
                    val burnedIdx = c.getColumnIndex("burnedCalories")
                    val adviceIdx = c.getColumnIndex("aiAdvice")
                    val tsIdx = c.getColumnIndex("aiAdviceTimestamp")
                    val modeIdx = c.getColumnIndex("goalMode")
                    val customCalIdx = c.getColumnIndex("customCalories")
                    val customPIdx = c.getColumnIndex("customProtein")
                    val customFIdx = c.getColumnIndex("customFat")
                    val customCIdx = c.getColumnIndex("customCarbs")
                    val dayTypeIdx = c.getColumnIndex("dayType")

                    DailySummary(
                        date = date,
                        burnedCalories = c.getInt(burnedIdx),
                        aiAdvice = c.getString(adviceIdx) ?: "",
                        aiAdviceTimestamp = c.getLong(tsIdx),
                        goalMode = if (modeIdx >= 0 && !c.isNull(modeIdx)) c.getString(modeIdx) else null,
                        customCalories = if (customCalIdx >= 0 && !c.isNull(customCalIdx)) c.getInt(customCalIdx) else null,
                        customProtein = if (customPIdx >= 0 && !c.isNull(customPIdx)) c.getFloat(customPIdx) else null,
                        customFat = if (customFIdx >= 0 && !c.isNull(customFIdx)) c.getFloat(customFIdx) else null,
                        customCarbs = if (customCIdx >= 0 && !c.isNull(customCIdx)) c.getFloat(customCIdx) else null,
                        dayType = if (dayTypeIdx >= 0 && !c.isNull(dayTypeIdx)) c.getString(dayTypeIdx) else null
                    )
                } else null
            }
        }

        override suspend fun insertOrUpdate(summary: DailySummary) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("date", summary.date)
                put("burnedCalories", summary.burnedCalories)
                put("aiAdvice", summary.aiAdvice)
                put("aiAdviceTimestamp", summary.aiAdviceTimestamp)
                if (summary.goalMode != null) put("goalMode", summary.goalMode) else putNull("goalMode")
                if (summary.customCalories != null) put("customCalories", summary.customCalories) else putNull("customCalories")
                if (summary.customProtein != null) put("customProtein", summary.customProtein) else putNull("customProtein")
                if (summary.customFat != null) put("customFat", summary.customFat) else putNull("customFat")
                if (summary.customCarbs != null) put("customCarbs", summary.customCarbs) else putNull("customCarbs")
                if (summary.dayType != null) put("dayType", summary.dayType) else putNull("dayType")
            }
            writableDatabase.insertWithOnConflict(
                "daily_summaries",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            notifyDataChanged()
        }

        override fun getRecordedDateCalendarInfo(fallbackMode: DietGoalMode): Flow<Map<String, DateCalendarInfo>> {
            return dbChangeNotifier.map {
                getRecordedDateCalendarInfoSync(fallbackMode)
            }
        }
    }

    private suspend fun getRecordedDateCalendarInfoSync(fallbackMode: DietGoalMode): Map<String, DateCalendarInfo> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, DateCalendarInfo>()

            // 1. 先读取 daily_summaries 中已保存的日期、模式和大小天
            val summaryCursor = readableDatabase.rawQuery(
                "SELECT date, burnedCalories, goalMode, dayType FROM daily_summaries",
                null
            )
            summaryCursor.use { c ->
                val dateIdx = c.getColumnIndex("date")
                val burnedIdx = c.getColumnIndex("burnedCalories")
                val modeIdx = c.getColumnIndex("goalMode")
                val dayTypeIdx = c.getColumnIndex("dayType")
                while (c.moveToNext()) {
                    val date = c.getString(dateIdx)
                    val burned = c.getInt(burnedIdx)
                    val modeStr = if (modeIdx >= 0 && !c.isNull(modeIdx)) c.getString(modeIdx) else null
                    val dayTypeStr = if (dayTypeIdx >= 0 && !c.isNull(dayTypeIdx)) c.getString(dayTypeIdx) else null
                    val parsedDayType = dayTypeStr?.let {
                        try { DayType.valueOf(it) } catch (_: Exception) { null }
                    }
                    val parsedMode = modeStr?.let {
                        try { DietGoalMode.valueOf(it) } catch (_: Exception) { null }
                    }

                    if (parsedMode != null) {
                        result[date] = DateCalendarInfo(parsedMode, parsedDayType)
                    } else if (burned > 0 || parsedDayType != null) {
                        result[date] = DateCalendarInfo(fallbackMode, parsedDayType)
                    }
                }
            }

            // 2. 补充只有 meal_records 但尚未写入 goalMode 的历史有记录日期
            val mealCursor = readableDatabase.rawQuery(
                "SELECT DISTINCT date FROM meal_records",
                null
            )
            mealCursor.use { c ->
                while (c.moveToNext()) {
                    val date = c.getString(0)
                    if (!result.containsKey(date)) {
                        result[date] = DateCalendarInfo(fallbackMode, null)
                    }
                }
            }

            result
        }

    private val _customFoodDao = object : CustomFoodDao {
        override fun getAllCustomFoods(): Flow<List<CustomFood>> {
            return dbChangeNotifier.map {
                getAllCustomFoodsSync()
            }
        }

        override suspend fun insertCustomFood(food: CustomFood): Long = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("name", food.name)
                put("unitType", food.unitType.name)
                put("calories", food.calories)
                put("protein", food.protein)
                put("fat", food.fat)
                put("carbs", food.carbs)
                put("timestamp", food.timestamp)
            }
            val id = writableDatabase.insert("custom_foods", null, values)
            notifyDataChanged()
            id
        }

        override suspend fun updateCustomFood(food: CustomFood) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("name", food.name)
                put("unitType", food.unitType.name)
                put("calories", food.calories)
                put("protein", food.protein)
                put("fat", food.fat)
                put("carbs", food.carbs)
            }
            writableDatabase.update("custom_foods", values, "id = ?", arrayOf(food.id.toString()))
            notifyDataChanged()
        }

        override suspend fun deleteCustomFood(food: CustomFood) = withContext(Dispatchers.IO) {
            writableDatabase.delete("custom_foods", "id = ?", arrayOf(food.id.toString()))
            notifyDataChanged()
        }

        override suspend fun getAllCustomFoodsSync(): List<CustomFood> = withContext(Dispatchers.IO) {
            val list = mutableListOf<CustomFood>()
            val cursor = readableDatabase.rawQuery(
                "SELECT * FROM custom_foods ORDER BY timestamp DESC",
                null
            )
            cursor.use { c ->
                val idIdx = c.getColumnIndex("id")
                val nameIdx = c.getColumnIndex("name")
                val unitIdx = c.getColumnIndex("unitType")
                val calIdx = c.getColumnIndex("calories")
                val pIdx = c.getColumnIndex("protein")
                val fIdx = c.getColumnIndex("fat")
                val cIdx = c.getColumnIndex("carbs")
                val tsIdx = c.getColumnIndex("timestamp")

                while (c.moveToNext()) {
                    val unitType = try {
                        FoodUnitType.valueOf(c.getString(unitIdx))
                    } catch (e: Exception) {
                        FoodUnitType.PER_100G
                    }
                    list.add(
                        CustomFood(
                            id = c.getLong(idIdx),
                            name = c.getString(nameIdx),
                            unitType = unitType,
                            calories = c.getInt(calIdx),
                            protein = c.getFloat(pIdx),
                            fat = c.getFloat(fIdx),
                            carbs = c.getFloat(cIdx),
                            timestamp = c.getLong(tsIdx)
                        )
                    )
                }
            }
            list
        }
    }

    private val _chatDao = object : ChatDao {
        override fun getMessagesByDate(date: String): Flow<List<ChatMessage>> {
            return dbChangeNotifier.map {
                getMessagesByDateSync(date)
            }
        }

        override suspend fun getMessagesByDateSync(date: String): List<ChatMessage> = withContext(Dispatchers.IO) {
            val list = mutableListOf<ChatMessage>()
            val cursor = readableDatabase.rawQuery(
                "SELECT * FROM chat_messages WHERE date = ? ORDER BY timestamp ASC, id ASC",
                arrayOf(date)
            )
            cursor.use { c ->
                val idIdx = c.getColumnIndex("id")
                val dateIdx = c.getColumnIndex("date")
                val roleIdx = c.getColumnIndex("role")
                val contentIdx = c.getColumnIndex("content")
                val tsIdx = c.getColumnIndex("timestamp")
                while (c.moveToNext()) {
                    list.add(
                        ChatMessage(
                            id = c.getLong(idIdx),
                            date = c.getString(dateIdx),
                            role = c.getString(roleIdx),
                            content = c.getString(contentIdx),
                            timestamp = c.getLong(tsIdx)
                        )
                    )
                }
            }
            list
        }

        override suspend fun insertMessage(message: ChatMessage): Long = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("date", message.date)
                put("role", message.role)
                put("content", message.content)
                put("timestamp", message.timestamp)
            }
            val id = writableDatabase.insert("chat_messages", null, values)
            notifyDataChanged()
            id
        }

        override suspend fun clearMessagesForDate(date: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("chat_messages", "date = ?", arrayOf(date))
            notifyDataChanged()
        }
    }

    private val _wikiDao = object : WikiDao {
        override fun getAllArticles(): Flow<List<WikiArticle>> {
            return dbChangeNotifier.map {
                getAllArticlesSync()
            }
        }

        override suspend fun getAllArticlesSync(): List<WikiArticle> = withContext(Dispatchers.IO) {
            val list = mutableListOf<WikiArticle>()
            val cursor = readableDatabase.rawQuery(
                "SELECT * FROM wiki_articles ORDER BY isBuiltin ASC, timestamp DESC",
                null
            )
            cursor.use { c ->
                val idIdx = c.getColumnIndex("id")
                val titleIdx = c.getColumnIndex("title")
                val catIdx = c.getColumnIndex("category")
                val contentIdx = c.getColumnIndex("content")
                val builtinIdx = c.getColumnIndex("isBuiltin")
                val tsIdx = c.getColumnIndex("timestamp")
                while (c.moveToNext()) {
                    list.add(
                        WikiArticle(
                            id = c.getLong(idIdx),
                            title = c.getString(titleIdx),
                            category = c.getString(catIdx),
                            content = c.getString(contentIdx),
                            isBuiltin = c.getInt(builtinIdx) == 1,
                            timestamp = c.getLong(tsIdx)
                        )
                    )
                }
            }
            list
        }

        override suspend fun insertArticle(article: WikiArticle): Long = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("title", article.title)
                put("category", article.category)
                put("content", article.content)
                put("isBuiltin", if (article.isBuiltin) 1 else 0)
                put("timestamp", article.timestamp)
            }
            val id = writableDatabase.insert("wiki_articles", null, values)
            notifyDataChanged()
            id
        }

        override suspend fun updateArticle(article: WikiArticle) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("title", article.title)
                put("category", article.category)
                put("content", article.content)
            }
            writableDatabase.update("wiki_articles", values, "id = ?", arrayOf(article.id.toString()))
            notifyDataChanged()
        }

        override suspend fun deleteArticle(article: WikiArticle) = withContext(Dispatchers.IO) {
            writableDatabase.delete("wiki_articles", "id = ?", arrayOf(article.id.toString()))
            notifyDataChanged()
        }
    }

    fun mealDao(): MealDao = _mealDao
    fun dailySummaryDao(): DailySummaryDao = _dailySummaryDao
    fun customFoodDao(): CustomFoodDao = _customFoodDao
    fun chatDao(): ChatDao = _chatDao
    fun wikiDao(): WikiDao = _wikiDao

    companion object {
        @Volatile
        private var INSTANCE: DietDatabase? = null

        fun getDatabase(context: Context): DietDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = DietDatabase(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
