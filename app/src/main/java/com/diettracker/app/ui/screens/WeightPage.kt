package com.diettracker.app.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.data.local.WeightDao
import com.diettracker.app.model.UserProfile
import com.diettracker.app.model.WEIGHT_TAGS
import com.diettracker.app.model.WeightRecord
import com.diettracker.app.ui.theme.GreenPrimary
import com.diettracker.app.ui.theme.OrangeWarning
import com.diettracker.app.ui.theme.RedDanger
import com.diettracker.app.util.WeightTrendUtils
import com.diettracker.app.util.WeightTrendUtils.FeedbackLevel
import com.diettracker.app.util.WeightTrendUtils.TrendPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ChartTimeRange(val label: String, val days: Int?) {
    DAYS_7("7天", 7),
    DAYS_30("30天", 30),
    DAYS_90("90天", 90),
    ALL("全部", null)
}

@Composable
fun WeightPage(
    weightDao: WeightDao,
    profile: UserProfile,
    currentDateStr: String,
    onSelectDate: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 观察所有体重记录（按日期升序）
    val allRecords by weightDao.getAllWeightRecords().collectAsState(initial = emptyList())

    // 计算 EMA 平滑趋势序列
    val trendPoints = remember(allRecords) {
        WeightTrendUtils.computeEMA(allRecords)
    }

    // 7 日趋势差值（EMA 最新 - 7天前）
    val weeklyTrendDelta = remember(trendPoints) {
        WeightTrendUtils.calculateWeeklyTrendDelta(trendPoints)
    }

    // 当天记录与当天趋势
    val todayRecord = remember(allRecords, currentDateStr) {
        allRecords.find { it.date == currentDateStr }
    }
    val todayTrendPoint = remember(trendPoints, currentDateStr) {
        trendPoints.find { it.record.date == currentDateStr }
    }
    // 最近一次有效记录（如果当天无记录时用于展示）
    val latestPoint = trendPoints.lastOrNull()
    val displayPoint = todayTrendPoint ?: latestPoint

    // 智能安抚反馈
    val feedback = remember(displayPoint, weeklyTrendDelta, trendPoints.size) {
        WeightTrendUtils.generateComfortFeedback(
            record = displayPoint?.record,
            trendWeight = displayPoint?.trendWeight,
            weeklyTrendDelta = weeklyTrendDelta,
            totalRecordsCount = trendPoints.size
        )
    }

    // 对话框状态
    var showEntryDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<WeightRecord?>(null) }
    var recordToDelete by remember { mutableStateOf<WeightRecord?>(null) }
    var selectedChartRange by remember { mutableStateOf(ChartTimeRange.DAYS_30) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 日期切换导航栏
        DateSelectorHeader(
            currentDateStr = currentDateStr,
            onSelectDate = onSelectDate
        )

        // 2. 核心指标卡片：原始体重 vs EMA 趋势 vs 7日变化
        WeightMetricsCard(
            currentDateStr = currentDateStr,
            todayRecord = todayRecord,
            todayTrendPoint = todayTrendPoint,
            latestPoint = latestPoint,
            weeklyTrendDelta = weeklyTrendDelta,
            onOpenEntry = {
                editingRecord = todayRecord
                showEntryDialog = true
            }
        )

        // 3. 智能安抚卡片（严禁制造焦虑）
        ComfortAdviceCard(feedback = feedback)

        // 4. 双层趋势图表卡片（Canvas 手绘）
        TrendChartCard(
            trendPoints = trendPoints,
            selectedRange = selectedChartRange,
            onRangeChanged = { selectedChartRange = it }
        )

        // 5. 历史记录列表区
        WeightHistorySection(
            records = allRecords.reversed(), // 倒序展示给用户看
            trendPointsMap = remember(trendPoints) {
                trendPoints.associate { it.record.date to it.trendWeight }
            },
            onEdit = { record ->
                editingRecord = record
                showEntryDialog = true
            },
            onDelete = { record ->
                recordToDelete = record
            }
        )
    }

    // 录入/编辑弹窗
    if (showEntryDialog) {
        WeightEntryDialog(
            initialRecord = editingRecord,
            fallbackDate = currentDateStr,
            lastWeight = latestPoint?.record?.weight ?: profile.weight,
            onDismiss = {
                showEntryDialog = false
                editingRecord = null
            },
            onSave = { record ->
                coroutineScope.launch {
                    weightDao.insertOrUpdate(record)
                    showEntryDialog = false
                    editingRecord = null
                    Toast.makeText(context, "体重记录已保存", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 删除确认弹窗
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("确认删除记录？", fontWeight = FontWeight.Bold) },
            text = { Text("将删除 ${recordToDelete?.date} 的体重数据（${recordToDelete?.weight} kg），删除后趋势线将自动重新计算。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDel = recordToDelete
                        recordToDelete = null
                        if (toDel != null) {
                            coroutineScope.launch {
                                weightDao.deleteWeightRecord(toDel.date)
                                Toast.makeText(context, "已删除该条记录", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedDanger)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 顶部日期导航栏（与今日饮食页风格一致）
 */
@Composable
private fun DateSelectorHeader(
    currentDateStr: String,
    onSelectDate: (String) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { dateFormat.format(Date()) }
    val isToday = currentDateStr == todayStr

    fun changeDay(offset: Int) {
        try {
            val cal = Calendar.getInstance()
            val parsed = dateFormat.parse(currentDateStr) ?: Date()
            cal.time = parsed
            cal.add(Calendar.DAY_OF_YEAR, offset)
            onSelectDate(dateFormat.format(cal.time))
        } catch (_: Exception) {}
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { changeDay(-1) }) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = "前一天",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val cal = Calendar.getInstance()
                        try {
                            dateFormat.parse(currentDateStr)?.let { cal.time = it }
                        } catch (_: Exception) {}
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val target = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                                onSelectDate(target)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "选择日期",
                    tint = GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isToday) "$currentDateStr 今天" else currentDateStr,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = { changeDay(1) }) {
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = "后一天",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 核心指标卡片：原始体重 vs EMA 趋势 vs 7日变化
 */
@Composable
private fun WeightMetricsCard(
    currentDateStr: String,
    todayRecord: WeightRecord?,
    todayTrendPoint: TrendPoint?,
    latestPoint: TrendPoint?,
    weeklyTrendDelta: Float,
    onOpenEntry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(GreenPrimary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonitorWeight,
                            contentDescription = null,
                            tint = GreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "体重与趋势概览",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (todayRecord != null) "已记录当天数据" else "当天未记录，展示最近参考",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onOpenEntry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (todayRecord != null) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (todayRecord != null) "修改记录" else "记录体重",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // 三项关键数据对齐
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. 原始称重
                val rawVal = todayRecord?.weight ?: latestPoint?.record?.weight
                val rawDeviation = if (todayRecord != null && todayTrendPoint != null) {
                    todayRecord.weight - todayTrendPoint.trendWeight
                } else null

                MetricItem(
                    label = if (todayRecord != null) "称重数值" else "最近称重",
                    value = if (rawVal != null) String.format(Locale.getDefault(), "%.1f", rawVal) else "--",
                    unit = "kg",
                    subText = if (rawDeviation != null) {
                        val sign = if (rawDeviation >= 0) "+" else ""
                        "偏离 ${sign}${String.format(Locale.getDefault(), "%.1f", rawDeviation)}"
                    } else if (todayRecord == null) {
                        latestPoint?.record?.date ?: "暂无"
                    } else "",
                    subColor = if (rawDeviation != null && rawDeviation > 0.3f) OrangeWarning else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 2. 真实趋势（EMA）
                val trendVal = todayTrendPoint?.trendWeight ?: latestPoint?.trendWeight
                MetricItem(
                    label = "趋势体重 (EMA)",
                    value = if (trendVal != null) String.format(Locale.getDefault(), "%.1f", trendVal) else "--",
                    unit = "kg",
                    subText = "平滑生理波动",
                    subColor = GreenPrimary
                )

                // 3. 7 日走向
                val deltaStr = if (trendPointsCountValid(latestPoint)) {
                    val sign = if (weeklyTrendDelta > 0) "+" else ""
                    "${sign}${String.format(Locale.getDefault(), "%.1f", weeklyTrendDelta)}"
                } else "--"

                val trendIcon = when {
                    weeklyTrendDelta < -0.1f -> Icons.Default.TrendingDown
                    weeklyTrendDelta > 0.1f -> Icons.Default.TrendingUp
                    else -> Icons.Default.TrendingFlat
                }
                val trendColor = when {
                    weeklyTrendDelta < -0.1f -> GreenPrimary
                    weeklyTrendDelta > 0.2f -> OrangeWarning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                MetricItem(
                    label = "7日趋势变化",
                    value = deltaStr,
                    unit = "kg",
                    subText = if (weeklyTrendDelta < -0.1f) "稳步减脂中" else if (weeklyTrendDelta > 0.2f) "略有上浮" else "保持稳定",
                    subColor = trendColor,
                    trailingIcon = trendIcon
                )
            }
        }
    }
}

private fun trendPointsCountValid(p: TrendPoint?): Boolean = p != null

@Composable
private fun MetricItem(
    label: String,
    value: String,
    unit: String,
    subText: String,
    subColor: Color,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unit,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
            if (trailingIcon != null && value != "--") {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = subColor,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(bottom = 3.dp)
                )
            }
        }
        if (subText.isNotBlank()) {
            Text(
                text = subText,
                fontSize = 11.sp,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 智能安抚卡片（严禁制造焦虑）
 */
@Composable
private fun ComfortAdviceCard(feedback: WeightTrendUtils.ComfortFeedback) {
    val bgColor = when (feedback.level) {
        FeedbackLevel.ENCOURAGING -> GreenPrimary.copy(alpha = 0.08f)
        FeedbackLevel.REASSURING -> OrangeWarning.copy(alpha = 0.08f)
        FeedbackLevel.STABLE -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when (feedback.level) {
        FeedbackLevel.ENCOURAGING -> GreenPrimary.copy(alpha = 0.25f)
        FeedbackLevel.REASSURING -> OrangeWarning.copy(alpha = 0.35f)
        FeedbackLevel.STABLE -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = when (feedback.level) {
                            FeedbackLevel.ENCOURAGING -> GreenPrimary.copy(alpha = 0.2f)
                            FeedbackLevel.REASSURING -> OrangeWarning.copy(alpha = 0.2f)
                            FeedbackLevel.STABLE -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (feedback.level) {
                        FeedbackLevel.ENCOURAGING -> "🌱"
                        FeedbackLevel.REASSURING -> "🛡️"
                        FeedbackLevel.STABLE -> "🧘"
                    },
                    fontSize = 18.sp
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = feedback.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = feedback.message,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 双层趋势图表卡片（Canvas 手绘）
 * 包含：时间维度切换、原始体重散点、EMA 平滑实线、点击悬浮 Tooltip
 */
@Composable
private fun TrendChartCard(
    trendPoints: List<TrendPoint>,
    selectedRange: ChartTimeRange,
    onRangeChanged: (ChartTimeRange) -> Unit
) {
    // 根据选择的时间维度过滤记录
    val filteredPoints = remember(trendPoints, selectedRange) {
        if (selectedRange.days == null) {
            trendPoints
        } else {
            trendPoints.takeLast(selectedRange.days)
        }
    }

    var selectedIndex by remember(filteredPoints) { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部维度切换与图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "体重走势图",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 范围选择分段 Chip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ChartTimeRange.entries.forEach { range ->
                        val isSelected = range == selectedRange
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) GreenPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable {
                                    onRangeChanged(range)
                                    selectedIndex = null
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = range.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 图例说明
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 散点图例
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(GreenPrimary.copy(alpha = 0.4f), CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "原始称重",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                // 实线图例
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(3.dp)
                        .background(GreenPrimary, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "真实趋势 (EMA)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (filteredPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无该时间段的体重数据\n点击上方“记录体重”开始你的第一笔记录吧！",
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Tooltip 浮窗信息（若选中了某个数据点）
                val activePoint = selectedIndex?.let { if (it in filteredPoints.indices) filteredPoints[it] else null }
                AnimatedVisibility(visible = activePoint != null) {
                    if (activePoint != null) {
                        val deviation = activePoint.record.weight - activePoint.trendWeight
                        val devSign = if (deviation >= 0) "+" else ""
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${activePoint.record.date} 称重详情",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (activePoint.record.tags.isNotEmpty()) {
                                        Text(
                                            text = "标签: " + activePoint.record.tags.joinToString("、"),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "原始 ${String.format(Locale.getDefault(), "%.1f", activePoint.record.weight)}kg",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "趋势 ${String.format(Locale.getDefault(), "%.1f", activePoint.trendWeight)}kg ($devSign${String.format(Locale.getDefault(), "%.1f", deviation)})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // 纯 Compose Canvas 手绘图表
                WeightTrendCanvas(
                    points = filteredPoints,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { selectedIndex = it }
                )
            }
        }
    }
}

/**
 * 纯手绘 Canvas 双层平滑趋势折线图
 */
@Composable
private fun WeightTrendCanvas(
    points: List<TrendPoint>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit
) {
    val density = LocalDensity.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 计算 Y 轴上下界（保留一点呼吸空间）
    val minWeight = points.minOf { min(it.record.weight, it.trendWeight) }
    val maxWeight = points.maxOf { max(it.record.weight, it.trendWeight) }
    val yMin = (minWeight - 0.8f).coerceAtLeast(0f)
    val yMax = maxWeight + 0.8f
    val yRange = if (yMax - yMin <= 0.1f) 1f else (yMax - yMin)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(points) {
                detectTapGestures { tapOffset ->
                    val paddingLeft = with(density) { 36.dp.toPx() }
                    val paddingRight = with(density) { 16.dp.toPx() }
                    val chartWidth = size.width - paddingLeft - paddingRight

                    if (points.size == 1) {
                        onSelectIndex(0)
                        return@detectTapGestures
                    }

                    val stepX = chartWidth / (points.size - 1)
                    val relativeX = tapOffset.x - paddingLeft
                    val nearestIndex = (relativeX / stepX + 0.5f).toInt().coerceIn(0, points.lastIndex)
                    onSelectIndex(nearestIndex)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingLeft = 36.dp.toPx()
            val paddingRight = 16.dp.toPx()
            val paddingTop = 16.dp.toPx()
            val paddingBottom = 28.dp.toPx()

            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            // 1. 绘制水平轻网格线与 Y 轴刻度线 (画 3~4 条)
            val gridCount = 3
            for (i in 0..gridCount) {
                val ratio = i.toFloat() / gridCount
                val y = paddingTop + chartHeight * (1f - ratio)
                drawLine(
                    color = gridColor,
                    start = Offset(paddingLeft, y),
                    end = Offset(size.width - paddingRight, y),
                    strokeWidth = 1f
                )
            }

            // 2. 映射所有点坐标
            val stepX = if (points.size > 1) chartWidth / (points.size - 1) else chartWidth / 2f
            val rawOffsets = points.mapIndexed { idx, p ->
                val x = paddingLeft + (if (points.size > 1) idx * stepX else chartWidth / 2f)
                val y = paddingTop + chartHeight * (1f - (p.record.weight - yMin) / yRange)
                Offset(x, y)
            }

            val trendOffsets = points.mapIndexed { idx, p ->
                val x = paddingLeft + (if (points.size > 1) idx * stepX else chartWidth / 2f)
                val y = paddingTop + chartHeight * (1f - (p.trendWeight - yMin) / yRange)
                Offset(x, y)
            }

            // 3. 绘制趋势平滑线 (EMA) 及渐变背景
            if (trendOffsets.size >= 2) {
                val path = Path().apply {
                    moveTo(trendOffsets[0].x, trendOffsets[0].y)
                    for (i in 0 until trendOffsets.size - 1) {
                        val p0 = trendOffsets[i]
                        val p1 = trendOffsets[i + 1]
                        val controlX1 = (p0.x + p1.x) / 2f
                        val controlY1 = p0.y
                        val controlX2 = (p0.x + p1.x) / 2f
                        val controlY2 = p1.y
                        cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                    }
                }

                // 阴影面积渐变
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(trendOffsets.last().x, paddingTop + chartHeight)
                    lineTo(trendOffsets.first().x, paddingTop + chartHeight)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GreenPrimary.copy(alpha = 0.22f),
                            GreenPrimary.copy(alpha = 0.02f)
                        ),
                        startY = paddingTop,
                        endY = paddingTop + chartHeight
                    )
                )

                // 粗实线
                drawPath(
                    path = path,
                    color = GreenPrimary,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            } else if (trendOffsets.size == 1) {
                drawCircle(
                    color = GreenPrimary,
                    radius = 5.dp.toPx(),
                    center = trendOffsets[0]
                )
            }

            // 4. 绘制原始称重散点 (淡色半透明)
            rawOffsets.forEachIndexed { idx, offset ->
                val isSel = selectedIndex == idx
                drawCircle(
                    color = if (isSel) GreenPrimary else GreenPrimary.copy(alpha = 0.40f),
                    radius = if (isSel) 6.dp.toPx() else 4.dp.toPx(),
                    center = offset
                )
                if (isSel) {
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = offset
                    )
                }
            }

            // 5. 选中数据点时的坚虚线指示
            if (selectedIndex != null && selectedIndex in points.indices) {
                val selectedX = rawOffsets[selectedIndex].x
                drawLine(
                    color = GreenPrimary.copy(alpha = 0.6f),
                    start = Offset(selectedX, paddingTop),
                    end = Offset(selectedX, paddingTop + chartHeight),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        // X 轴起止日期标记
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 36.dp, end = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = points.firstOrNull()?.record?.date?.substring(5) ?: "",
                fontSize = 10.sp,
                color = textPaintColor
            )
            if (points.size > 2) {
                Text(
                    text = points[points.size / 2].record.date.substring(5),
                    fontSize = 10.sp,
                    color = textPaintColor
                )
            }
            Text(
                text = points.lastOrNull()?.record?.date?.substring(5) ?: "",
                fontSize = 10.sp,
                color = textPaintColor
            )
        }
    }
}

/**
 * 历史体重记录折叠/列表区
 */
@Composable
private fun WeightHistorySection(
    records: List<WeightRecord>,
    trendPointsMap: Map<String, Float>,
    onEdit: (WeightRecord) -> Unit,
    onDelete: (WeightRecord) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "历史记录 (${records.size}条)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                if (records.isEmpty()) {
                    Text(
                        text = "暂无历史体重记录",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        records.forEach { record ->
                            val trendVal = trendPointsMap[record.date]
                            HistoryRecordItem(
                                record = record,
                                trendWeight = trendVal,
                                onEdit = { onEdit(record) },
                                onDelete = { onDelete(record) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordItem(
    record: WeightRecord,
    trendWeight: Float?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = record.date,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.1f", record.weight)} kg",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GreenPrimary
                    )
                    if (trendWeight != null) {
                        Text(
                            text = "(趋势: ${String.format(Locale.getDefault(), "%.1f", trendWeight)})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 标签展示
                if (record.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        record.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GreenPrimary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    color = GreenPrimary
                                )
                            }
                        }
                    }
                }

                // 腰围/体脂率及备注
                val extras = buildList {
                    if (record.waist != null) add("腰围 ${record.waist}cm")
                    if (record.bodyFat != null) add("体脂 ${record.bodyFat}%")
                    if (record.note.isNotBlank()) add(record.note)
                }.joinToString(" · ")

                if (extras.isNotBlank()) {
                    Text(
                        text = extras,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = RedDanger.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 体重录入/修改弹窗
 * - 默认预填最近一次数值，支持手动输入与 ±0.1kg 快捷微调
 * - 支持补录选择日期
 * - 轻提示：“建议晨起空腹排便后称重，数据更准确”
 * - 状态标签采用多选胶囊按钮
 * - 腰围和体脂率折叠在“更多体测数据（选填）”中
 */
@Composable
fun WeightEntryDialog(
    initialRecord: WeightRecord?,
    fallbackDate: String,
    lastWeight: Float,
    onDismiss: () -> Unit,
    onSave: (WeightRecord) -> Unit
) {
    val context = LocalContext.current
    val isEditMode = initialRecord != null

    var selectedDate by remember {
        mutableStateOf(initialRecord?.date ?: fallbackDate)
    }

    var weightText by remember {
        val initialVal = initialRecord?.weight ?: lastWeight
        mutableStateOf(String.format(Locale.getDefault(), "%.1f", initialVal))
    }

    val selectedTags = remember {
        mutableStateListOf<String>().apply {
            initialRecord?.tags?.let { addAll(it) }
        }
    }

    var isMoreExpanded by remember { mutableStateOf(false) }
    var waistText by remember { mutableStateOf(initialRecord?.waist?.toString() ?: "") }
    var bodyFatText by remember { mutableStateOf(initialRecord?.bodyFat?.toString() ?: "") }
    var noteText by remember { mutableStateOf(initialRecord?.note ?: "") }

    fun adjustWeight(delta: Float) {
        val curr = weightText.toFloatOrNull() ?: lastWeight
        val next = (curr + delta).coerceAtLeast(20.0f)
        weightText = String.format(Locale.getDefault(), "%.1f", next)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 弹窗标题与日期选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "修改体重记录" else "记录今日体重",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 补录日期选择器
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                val cal = Calendar.getInstance()
                                try {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)?.let {
                                        cal.time = it
                                    }
                                } catch (_: Exception) {}
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = GreenPrimary
                        )
                        Text(
                            text = selectedDate,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 核心数字录入与 ±0.1kg 快捷按钮
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // -0.1 按钮
                        FilledIconButton(
                            onClick = { adjustWeight(-0.1f) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "减 0.1 kg",
                                tint = GreenPrimary
                            )
                        }

                        // 数字输入
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { weightText = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.width(130.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "kg",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // +0.1 按钮
                        FilledIconButton(
                            onClick = { adjustWeight(0.1f) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "加 0.1 kg",
                                tint = GreenPrimary
                            )
                        }
                    }

                    // 科学空腹排便提示
                    Text(
                        text = "💡 建议晨起空腹排便后称重，数据更准确",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 状态标签（可多选的轻量胶囊按钮 Chip）
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "状态标签（帮助 AI 识别生理波动）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WEIGHT_TAGS.forEach { tag ->
                            val isSelected = tag in selectedTags
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        selectedTags.remove(tag)
                                    } else {
                                        selectedTags.add(tag)
                                    }
                                },
                                label = {
                                    Text(
                                        text = tag,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GreenPrimary.copy(alpha = 0.16f),
                                    selectedLabelColor = GreenPrimary
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }

                // 更多体测数据折叠面板（腰围、体脂率、备注）
                Column(
                    modifier = Modifier.animateContentSize(tween(200)),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isMoreExpanded = !isMoreExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "更多体测数据（选填）",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (isMoreExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isMoreExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = waistText,
                                onValueChange = { waistText = it },
                                label = { Text("腰围 (cm)") },
                                placeholder = { Text("例 75.0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = bodyFatText,
                                onValueChange = { bodyFatText = it },
                                label = { Text("体脂率 (%)") },
                                placeholder = { Text("例 15.5") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text("备注（选填）") },
                            placeholder = { Text("如：换季水肿、昨晚大餐火锅等") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val w = weightText.toFloatOrNull()
                            if (w == null || w <= 10f || w >= 300f) {
                                Toast.makeText(context, "请输入合理的体重数值（20~250 kg）", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val waist = waistText.toFloatOrNull()
                            val bodyFat = bodyFatText.toFloatOrNull()

                            val record = WeightRecord(
                                date = selectedDate,
                                weight = w,
                                tags = selectedTags.toList(),
                                waist = waist,
                                bodyFat = bodyFat,
                                note = noteText.trim(),
                                timestamp = System.currentTimeMillis()
                            )
                            onSave(record)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Text(
                            text = if (isEditMode) "更新记录" else "保存记录",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
