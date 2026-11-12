package com.diettracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.model.DateCalendarInfo
import com.diettracker.app.model.DietGoalMode
import com.diettracker.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

fun modeCalendarColor(mode: DietGoalMode): Color = when (mode) {
    DietGoalMode.FAT_LOSS -> BlueAccent
    DietGoalMode.MUSCLE_GAIN -> RedDanger
    DietGoalMode.MAINTENANCE -> GreenPrimary
    DietGoalMode.CUSTOM -> YellowCustom
}

@Composable
fun CalendarDialog(
    selectedDateStr: String,
    dateInfos: Map<String, DateCalendarInfo>,
    title: String = "饮食模式日历",
    subtitle: String = "点击任意日期，即可跳转查看当天的热量轮盘",
    onSelectDate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { dateFormat.format(Date()) }

    // 初始展示当前所选日期所在的月份
    var displayYearMonth by remember(selectedDateStr) {
        val cal = Calendar.getInstance()
        try {
            cal.time = dateFormat.parse(selectedDateStr) ?: Date()
        } catch (_: Exception) {
        }
        mutableStateOf(Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)))
    }

    val year = displayYearMonth.first
    val month = displayYearMonth.second // 0-indexed

    val monthCells = remember(year, month) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // Sunday = 0
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val cells = mutableListOf<String?>()
        repeat(firstDayOfWeek) { cells.add(null) }
        for (day in 1..daysInMonth) {
            cells.add(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
        }
        while (cells.size % 7 != 0) {
            cells.add(null)
        }
        cells
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { onSelectDate(todayStr) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("今天", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 颜色图例与大小天说明
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendDot("减脂", BlueAccent)
                    LegendDot("增肌", RedDanger)
                    LegendDot("维持", GreenPrimary)
                    LegendDot("自定义", YellowCustom)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "方块内小字标注当天状态：训练日 / 休息日 / 放纵日",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 月份切换栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val newMonth = if (month == 0) 11 else month - 1
                        val newYear = if (month == 0) year - 1 else year
                        displayYearMonth = Pair(newYear, newMonth)
                    }) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "上个月", modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = "${year}年 ${month + 1}月",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = {
                        val newMonth = if (month == 11) 0 else month + 1
                        val newYear = if (month == 11) year + 1 else year
                        displayYearMonth = Pair(newYear, newMonth)
                    }) {
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = "下个月", modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 星期表头
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { weekDay ->
                        Text(
                            text = weekDay,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 月历日期格子（圆角方块，同时展示模式底色与大小天文字）
                monthCells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        week.forEach { dateStr ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.84f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dateStr != null) {
                                    val dayNum = dateStr.substringAfterLast("-").toIntOrNull()?.toString() ?: ""
                                    val info = dateInfos[dateStr]
                                    val mode = info?.goalMode
                                    val dayType = info?.dayType
                                    val isSelected = dateStr == selectedDateStr
                                    val isToday = dateStr == todayStr

                                    val bgColor = if (mode != null) modeCalendarColor(mode) else Color.Transparent
                                    val textColor = when {
                                        mode == DietGoalMode.CUSTOM -> Color.Black
                                        mode != null -> Color.White
                                        isToday -> GreenPrimary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    val cellShape = RoundedCornerShape(10.dp)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.92f)
                                            .clip(cellShape)
                                            .background(bgColor)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, cellShape)
                                                } else if (isToday && mode == null) {
                                                    Modifier.border(1.dp, GreenPrimary, cellShape)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .clickable { onSelectDate(dateStr) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = dayNum,
                                                fontSize = 13.sp,
                                                lineHeight = 14.sp,
                                                fontWeight = if (mode != null || isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = textColor
                                            )
                                            if (dayType != null) {
                                                Text(
                                                    text = dayType.shortLabel,
                                                    fontSize = 8.sp,
                                                    lineHeight = 9.sp,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    fontWeight = FontWeight.Medium,
                                                    color = textColor.copy(alpha = 0.92f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
