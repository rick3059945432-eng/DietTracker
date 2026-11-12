package com.diettracker.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.data.local.DietDatabase
import com.diettracker.app.data.preferences.UserPreferencesManager
import com.diettracker.app.data.remote.OpenAIService
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.diettracker.app.R
import com.diettracker.app.model.AppPage
import com.diettracker.app.model.DailySummary
import com.diettracker.app.model.DayType
import com.diettracker.app.model.GoalConfig
import com.diettracker.app.model.MealRecord
import com.diettracker.app.model.MealType
import com.diettracker.app.model.calculateDailyNutritionTargets
import com.diettracker.app.ui.components.*
import com.diettracker.app.ui.theme.GreenPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    db: DietDatabase,
    prefsManager: UserPreferencesManager,
    openAIService: OpenAIService,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val profile by prefsManager.userProfile.collectAsState()
    val apiConfig by prefsManager.apiConfig.collectAsState()
    val appearance by prefsManager.appearanceConfig.collectAsState()
    val globalGoalConfig by prefsManager.goalConfig.collectAsState()

    // 当前选中的日期 (YYYY-MM-DD)
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val currentDateStr = dateFormat.format(selectedCalendar.time)

    // 今天日期对比
    val todayStr = remember { dateFormat.format(Date()) }
    val isToday = currentDateStr == todayStr

    // 数据库查询数据
    val mealDao = db.mealDao()
    val summaryDao = db.dailySummaryDao()
    val chatDao = db.chatDao()
    val wikiDao = db.wikiDao()

    val mealRecords by mealDao.getMealsByDate(currentDateStr).collectAsState(initial = emptyList())
    val dailySummary by summaryDao.getDailySummary(currentDateStr).collectAsState(initial = null)
    val dateInfos by summaryDao.getRecordedDateCalendarInfo(globalGoalConfig.mode).collectAsState(initial = emptyMap())

    // 弹窗状态
    var addingMealType by remember { mutableStateOf<MealType?>(null) }
    var copyingTargetMealType by remember { mutableStateOf<MealType?>(null) }
    var editingMealRecord by remember { mutableStateOf<MealRecord?>(null) }
    var showGoalModeDialog by remember { mutableStateOf(false) }
    var showDayTypeDialog by remember { mutableStateOf(false) }
    var isDailyAutoPrompt by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var showBurnedDialog by remember { mutableStateOf(false) }

    // 可扩展活页状态（支持左右滑动切换及未来新增第 3、4 页）
    val pages = AppPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // 每天首次打开自动弹一次三选一（仅当今天尚未选过大小天且今天未弹过时）
    LaunchedEffect(todayStr) {
        if (!prefsManager.hasPromptedDayTypeToday(todayStr)) {
            val todaySum = summaryDao.getDailySummarySync(todayStr)
            prefsManager.markDayTypePromptedToday(todayStr)
            if (todaySum?.dayType == null) {
                isDailyAutoPrompt = true
                showDayTypeDialog = true
            }
        }
    }

    // 计算当前日期独立的模式、大小天与目标
    val effectiveGoalConfig = remember(dailySummary, globalGoalConfig) {
        dailySummary?.toGoalConfig(globalGoalConfig) ?: globalGoalConfig
    }
    val currentDayType = remember(dailySummary) { dailySummary?.parsedDayType() }
    val totalCalories = mealRecords.sumOf { it.calories }
    val totalProtein = mealRecords.map { it.protein }.sum()
    val totalFat = mealRecords.map { it.fat }.sum()
    val totalCarbs = mealRecords.map { it.carbs }.sum()
    val burnedCalories = dailySummary?.burnedCalories ?: 0
    val targets = remember(profile, burnedCalories, effectiveGoalConfig, currentDayType) {
        calculateDailyNutritionTargets(profile, burnedCalories, effectiveGoalConfig, currentDayType)
    }

    fun summaryWithCurrentMode(
        base: DailySummary?,
        goal: GoalConfig,
        dayTypeOverride: DayType? = base?.parsedDayType()
    ): DailySummary {
        val current = base ?: DailySummary(date = currentDateStr)
        return current.copy(
            goalMode = goal.mode.name,
            customCalories = if (goal.mode.name == "CUSTOM") goal.customCalories else current.customCalories,
            customProtein = if (goal.mode.name == "CUSTOM") goal.customProtein else current.customProtein,
            customFat = if (goal.mode.name == "CUSTOM") goal.customFat else current.customFat,
            customCarbs = if (goal.mode.name == "CUSTOM") goal.customCarbs else current.customCarbs,
            dayType = dayTypeOverride?.name ?: current.dayType
        )
    }

    CustomBackgroundScaffold(
        isDarkMode = appearance.isDarkMode,
        customBgUri = appearance.customBgUri,
        bgOpacity = appearance.bgOpacity
    ) {
        val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(
                    if (isImeVisible) {
                        WindowInsets.navigationBars.union(WindowInsets.ime)
                    } else {
                        WindowInsets(0, 0, 0, 0)
                    }
                )
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. 顶部日期选择器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val cal = selectedCalendar.clone() as Calendar
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    selectedCalendar = cal
                }) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Prev Day", modifier = Modifier.size(18.dp))
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showCalendarDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "打开日历",
                            tint = GreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isToday) "$currentDateStr 今天" else currentDateStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(onClick = {
                    val cal = selectedCalendar.clone() as Calendar
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    selectedCalendar = cal
                }) {
                    Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next Day", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. 左右滑动活页区
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) { pageIndex ->
                when (pages[pageIndex]) {
                    AppPage.DIET_DASHBOARD -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 激励横幅与个人头像入口
                            val quote = remember(profile.nickname) { prefsManager.getDailyQuote(profile.nickname) }
                            QuoteBanner(
                                nickname = profile.nickname,
                                avatarUri = profile.avatarUri,
                                quoteText = quote,
                                onAvatarClick = onNavigateToSettings
                            )

                            // 热量与碳蛋脂四圆环仪表盘
                            CalorieDashboardCard(
                                currentCalories = totalCalories,
                                baseMaintenanceCalories = profile.maintenanceCalories,
                                burnedCalories = burnedCalories,
                                targets = targets,
                                protein = totalProtein,
                                fat = totalFat,
                                carbs = totalCarbs,
                                onSelectModeClick = { showGoalModeDialog = true },
                                onSelectDayTypeClick = {
                                    isDailyAutoPrompt = false
                                    showDayTypeDialog = true
                                },
                                onEditBurnedCalories = { showBurnedDialog = true }
                            )

                            // 四餐卡片
                            MealType.entries.forEach { type ->
                                MealSectionCard(
                                    mealType = type,
                                    records = mealRecords.filter { it.mealType == type },
                                    onAddClick = { addingMealType = type },
                                    onCopyHistoryClick = { copyingTargetMealType = type },
                                    onEditRecord = { rec -> editingMealRecord = rec },
                                    onDeleteRecord = { rec -> coroutineScope.launch { mealDao.deleteMeal(rec) } }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    AppPage.AI_COACH -> {
                        AiCoachPage(
                            dateStr = currentDateStr,
                            profile = profile,
                            meals = mealRecords,
                            burnedCalories = burnedCalories,
                            targets = targets,
                            apiConfig = apiConfig,
                            chatDao = chatDao,
                            wikiDao = wikiDao,
                            openAIService = openAIService,
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }

                    AppPage.FITNESS_WIKI -> {
                        WikiPage(
                            wikiDao = wikiDao,
                            apiConfig = apiConfig,
                            openAIService = openAIService,
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }
                }
            }

            // 3. 底部导航栏（键盘弹出时自动隐藏，避免与输入框抢占空间）
            if (!isImeVisible) {
                DietBottomNavigationBar(
                    pages = pages,
                    currentPageIndex = pagerState.currentPage,
                    onSelectPage = { index ->
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
            }
        }
    }

    // 录入餐食弹窗
    if (addingMealType != null) {
        val currentType = addingMealType!!
        AddMealDialog(
            mealType = currentType,
            dateString = currentDateStr,
            apiConfig = apiConfig,
            openAIService = openAIService,
            customFoodDao = db.customFoodDao(),
            onCopyHistoryClick = {
                addingMealType = null
                copyingTargetMealType = currentType
            },
            onDismiss = { addingMealType = null },
            onSave = { newMeal ->
                coroutineScope.launch {
                    mealDao.insertMeal(newMeal)
                    // 为当前日期落盘模式颜色，便于在日历中展示
                    if (dailySummary?.goalMode == null) {
                        summaryDao.insertOrUpdate(summaryWithCurrentMode(dailySummary, effectiveGoalConfig))
                    }
                    addingMealType = null
                    Toast.makeText(context, "已成功记录！", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 单条餐食独立编辑弹窗
    if (editingMealRecord != null) {
        EditMealDialog(
            record = editingMealRecord!!,
            onDismiss = { editingMealRecord = null },
            onSave = { updated ->
                coroutineScope.launch {
                    mealDao.updateMeal(updated)
                    editingMealRecord = null
                    Toast.makeText(context, "修改已保存！", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 修改运动消耗弹窗
    if (showBurnedDialog) {
        var inputBurned by remember { mutableStateOf(burnedCalories.toString()) }
        Dialog(onDismissRequest = { showBurnedDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("填写今日运动/额外消耗", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputBurned,
                        onValueChange = { inputBurned = it },
                        label = { Text("消耗千卡 (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showBurnedDialog = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val valBurned = inputBurned.toIntOrNull() ?: 0
                            coroutineScope.launch {
                                summaryDao.insertOrUpdate(
                                    summaryWithCurrentMode(dailySummary, effectiveGoalConfig).copy(
                                        burnedCalories = valBurned
                                    )
                                )
                                showBurnedDialog = false
                            }
                        }) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    // 营养模式切换弹窗
    if (showGoalModeDialog) {
        GoalModeDialog(
            currentConfig = effectiveGoalConfig,
            profile = profile,
            burnedCalories = burnedCalories,
            onDismiss = { showGoalModeDialog = false },
            onConfirm = { newConfig ->
                coroutineScope.launch {
                    summaryDao.insertOrUpdate(summaryWithCurrentMode(dailySummary, newConfig))
                    prefsManager.saveGoalConfig(newConfig)
                    showGoalModeDialog = false
                    Toast.makeText(context, "已将该日设为「${newConfig.mode.displayName}」", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 彩色饮食模式日历弹窗
    if (showCalendarDialog) {
        CalendarDialog(
            selectedDateStr = currentDateStr,
            dateInfos = dateInfos,
            onSelectDate = { pickedDate ->
                try {
                    val parsed = dateFormat.parse(pickedDate)
                    if (parsed != null) {
                        val cal = Calendar.getInstance()
                        cal.time = parsed
                        selectedCalendar = cal
                    }
                } catch (_: Exception) {
                }
                showCalendarDialog = false
            },
            onDismiss = { showCalendarDialog = false }
        )
    }

    // 训练日 / 休息日 / 放纵日 详情三选一弹窗
    if (showDayTypeDialog) {
        val targetDateForDialog = if (isDailyAutoPrompt) todayStr else currentDateStr
        DayTypeDialog(
            dateStr = targetDateForDialog,
            currentDayType = if (isDailyAutoPrompt) null else currentDayType,
            goalConfig = effectiveGoalConfig,
            profile = profile,
            burnedCalories = burnedCalories,
            showSkipButton = isDailyAutoPrompt,
            onDismiss = { showDayTypeDialog = false },
            onConfirm = { chosenDayType ->
                coroutineScope.launch {
                    val existing = summaryDao.getDailySummarySync(targetDateForDialog)
                    val base = existing ?: DailySummary(date = targetDateForDialog)
                    val goalToStamp = existing?.toGoalConfig(globalGoalConfig) ?: globalGoalConfig
                    summaryDao.insertOrUpdate(
                        base.copy(
                            goalMode = base.goalMode ?: goalToStamp.mode.name,
                            customCalories = if (goalToStamp.mode.name == "CUSTOM") (base.customCalories ?: goalToStamp.customCalories) else base.customCalories,
                            customProtein = if (goalToStamp.mode.name == "CUSTOM") (base.customProtein ?: goalToStamp.customProtein) else base.customProtein,
                            customFat = if (goalToStamp.mode.name == "CUSTOM") (base.customFat ?: goalToStamp.customFat) else base.customFat,
                            customCarbs = if (goalToStamp.mode.name == "CUSTOM") (base.customCarbs ?: goalToStamp.customCarbs) else base.customCarbs,
                            dayType = chosenDayType.name
                        )
                    )
                    showDayTypeDialog = false
                    Toast.makeText(
                        context,
                        "已将 $targetDateForDialog 设为「${chosenDayType.displayName}」",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // 「同历史 / 同历史某次」跨天跨餐覆盖复制弹窗
    if (copyingTargetMealType != null) {
        val targetType = copyingTargetMealType!!
        CopyFromHistoryDialog(
            targetDateStr = currentDateStr,
            targetMealType = targetType,
            dateInfos = dateInfos,
            mealDao = mealDao,
            onConfirmCopy = { sourceDate, sourceMealType, sourceMeals ->
                coroutineScope.launch {
                    mealDao.replaceMealsForDateAndType(
                        date = currentDateStr,
                        mealType = targetType,
                        sourceMeals = sourceMeals
                    )
                    if (dailySummary?.goalMode == null) {
                        summaryDao.insertOrUpdate(summaryWithCurrentMode(dailySummary, effectiveGoalConfig))
                    }
                    copyingTargetMealType = null
                    Toast.makeText(
                        context,
                        "已将 $sourceDate ${sourceMealType.displayName} 覆盖填入当前 ${targetType.displayName}！",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { copyingTargetMealType = null }
        )
    }
}

@Composable
private fun DietBottomNavigationBar(
    pages: List<AppPage>,
    currentPageIndex: Int,
    onSelectPage: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, page ->
                    val isSelected = currentPageIndex == index
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) GreenPrimary else Color.Transparent,
                        label = "BottomNavPillColor"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "BottomNavLabelColor"
                    )
                    val (outlineIconRes, filledIconRes) = when (page) {
                        AppPage.DIET_DASHBOARD -> R.drawable.ic_broccoli_outline to R.drawable.ic_broccoli_filled
                        AppPage.AI_COACH -> R.drawable.ic_arm_dumbbell_outline to R.drawable.ic_arm_dumbbell_filled
                        AppPage.FITNESS_WIKI -> R.drawable.ic_book_outline to R.drawable.ic_book_filled
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectPage(index) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(32.dp)
                                .background(
                                    color = pillColor,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isSelected) filledIconRes else outlineIconRes
                                ),
                                contentDescription = page.title,
                                tint = if (isSelected) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
