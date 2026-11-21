package com.diettracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.window.DialogProperties
import com.diettracker.app.model.FatLossRate
import com.diettracker.app.model.WeeklyReviewReport
import com.diettracker.app.ui.theme.GreenPrimary
import com.diettracker.app.ui.theme.OrangeWarning
import com.diettracker.app.ui.theme.RedDanger
import java.util.Locale
import kotlin.math.abs

@Composable
fun WeeklyReviewDialog(
    report: WeeklyReviewReport,
    currentRate: FatLossRate,
    onRateChanged: (FatLossRate) -> Unit,
    onDismiss: () -> Unit,
    onApplyTargetCalories: (Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 1. 顶部 Header 与关闭按钮
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
                                .background(GreenPrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "周度代谢与减脂复盘",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${report.startDate} ~ ${report.endDate}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 可滑动正文内容
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 2. 目标减脂速率选择 Chip 栏
                    RateSelectionSection(
                        selectedRate = currentRate,
                        onSelectRate = onRateChanged
                    )

                    // 3. 核心指标四宫格（日均摄入、趋势体重变化、真实TDEE、目标速率对比）
                    ReviewMetricsGrid(report = report)

                    // 4. 体测数据辅助交叉验证（如果有记录腰围/体脂率）
                    if (report.waistDelta != null || report.bodyFatDelta != null) {
                        BodyMeasurementsCrossCheckCard(
                            waistDelta = report.waistDelta,
                            bodyFatDelta = report.bodyFatDelta
                        )
                    }

                    // 5. 热量微调建议卡片（核心建议）
                    CalorieAdjustmentProposalCard(report = report)

                    // 6. 平台期破局指导（若触底且停滞）
                    if (report.isPlateauAtFloor && report.plateauAdvice != null) {
                        PlateauBreakCard(advice = report.plateauAdvice)
                    }

                    // 7. 算法原理与科学声明
                    ScientificFootnoteCard()
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 8. 底部操作按钮：严格遵照用户确认机制（严禁静默修改）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "暂不调整，保持现状",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = {
                            onApplyTargetCalories(report.suggestedTargetCalories)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (report.calorieAdjustment != 0) "采纳并更新热量目标" else "确认当前最佳节奏",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 减脂速率选择器
 */
@Composable
private fun RateSelectionSection(
    selectedRate: FatLossRate,
    onSelectRate: (FatLossRate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "目标节奏偏好",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FatLossRate.entries.forEach { rate ->
                val isSelected = rate == selectedRate
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectRate(rate) },
                    label = {
                        Text(
                            text = rate.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenPrimary.copy(alpha = 0.16f),
                        selectedLabelColor = GreenPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

/**
 * 4 项核心看板指标网格
 */
@Composable
private fun ReviewMetricsGrid(report: WeeklyReviewReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一行：日均摄入 + 趋势体重变化
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricGridItem(
                    title = "7日日均摄入",
                    value = "${report.avgDailyIntake}",
                    unit = "kcal",
                    subTitle = "有效记录 ${report.validDietDays}/7 天",
                    modifier = Modifier.weight(1f)
                )

                val deltaSign = if (report.trendDeltaKg > 0) "+" else ""
                val deltaStr = "$deltaSign${String.format(Locale.getDefault(), "%.2f", report.trendDeltaKg)}"
                MetricGridItem(
                    title = "趋势体重变化",
                    value = deltaStr,
                    unit = "kg",
                    subTitle = "目标约 -${String.format(Locale.getDefault(), "%.2f", report.targetWeeklyLossKg)} kg",
                    highlightColor = if (report.trendDeltaKg < 0) GreenPrimary else OrangeWarning,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 第二行：反推真实 TDEE + 当前目标对比
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tdeeDiff = report.estimatedTDEE - report.baselineMaintenanceCalories
                val diffSign = if (tdeeDiff >= 0) "+" else ""
                MetricGridItem(
                    title = "反推真实 TDEE",
                    value = "${report.estimatedTDEE}",
                    unit = "kcal",
                    subTitle = "比初始基线 $diffSign$tdeeDiff kcal",
                    highlightColor = GreenPrimary,
                    modifier = Modifier.weight(1f)
                )

                MetricGridItem(
                    title = "当前执行目标",
                    value = "${report.currentTargetCalories}",
                    unit = "kcal",
                    subTitle = "下限底线 ${report.safetyFloor} kcal",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricGridItem(
    title: String,
    value: String,
    unit: String,
    subTitle: String,
    highlightColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = highlightColor
            )
            Text(
                text = unit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            text = subTitle,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 体测辅助交叉验证卡片（腰围/体脂）
 */
@Composable
private fun BodyMeasurementsCrossCheckCard(
    waistDelta: Float?,
    bodyFatDelta: Float?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GreenPrimary.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(GreenPrimary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "体测交叉验证",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val parts = buildList {
                    if (waistDelta != null) {
                        val sign = if (waistDelta > 0) "+" else ""
                        add("腰围 $sign${String.format(Locale.getDefault(), "%.1f", waistDelta)} cm")
                    }
                    if (bodyFatDelta != null) {
                        val sign = if (bodyFatDelta > 0) "+" else ""
                        add("体脂率 $sign${String.format(Locale.getDefault(), "%.1f", bodyFatDelta)} %")
                    }
                }.joinToString("，")

                val conclusion = if ((waistDelta ?: 0f) < 0 || (bodyFatDelta ?: 0f) < 0) {
                    "—— 体围正在收紧，说明即使体重受水分干扰，纯脂肪仍在稳定减少！"
                } else {
                    "—— 持续记录体测数据，能帮你多维度看清真实减脂成果。"
                }
                Text(
                    text = "$parts $conclusion",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 热量微调建议卡片
 */
@Composable
private fun CalorieAdjustmentProposalCard(report: WeeklyReviewReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (report.calorieAdjustment != 0) GreenPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "每日热量自适应校准",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 调整幅度微标
                val badgeText = when {
                    report.calorieAdjustment > 0 -> "+${report.calorieAdjustment} kcal"
                    report.calorieAdjustment < 0 -> "${report.calorieAdjustment} kcal"
                    else -> "保持不变"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (report.calorieAdjustment != 0) GreenPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (report.calorieAdjustment != 0) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 当前 -> 建议 对比栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "当前每日目标", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${report.currentTargetCalories} kcal",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "建议新目标", fontSize = 11.sp, color = GreenPrimary)
                    Text(
                        text = "${report.suggestedTargetCalories} kcal",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary
                    )
                }
            }

            // 科学分析文案
            Text(
                text = report.explanation,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 安全护栏底线提醒
            if (report.isAtSafetyFloor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OrangeWarning.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = OrangeWarning,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "已启用基础代谢安全底线（${report.safetyFloor} kcal），绝不进一步下调，守护肌肉与健康。",
                        fontSize = 11.sp,
                        color = OrangeWarning
                    )
                }
            }
        }
    }
}

/**
 * 平台期破局建议卡片
 */
@Composable
private fun PlateauBreakCard(advice: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeWarning.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, OrangeWarning.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "🧗", fontSize = 16.sp)
                Text(
                    text = "平台期触底破局指南（无需节食）",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = advice,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 科学计算原理与免责脚标
 */
@Composable
private fun ScientificFootnoteCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "自适应校准基于能量守恒定律：TDEE = 摄入 - (趋势体重Δ × 7700 / 天数)。单次调整步长受 ±150 kcal 护栏保护，避免剧烈跳变。点击采纳后将自动同步更新到每日目标。",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
