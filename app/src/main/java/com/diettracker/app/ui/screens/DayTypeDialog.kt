package com.diettracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.model.*
import com.diettracker.app.ui.theme.*
import com.diettracker.app.util.ImageStorageUtils

@Composable
fun DayTypeDialog(
    dateStr: String,
    currentDayType: DayType?,
    goalConfig: GoalConfig,
    profile: UserProfile,
    burnedCalories: Int,
    showSkipButton: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (DayType) -> Unit
) {
    var previewDayType by remember { mutableStateOf(currentDayType ?: DayType.TRAINING) }
    val previewTargets = remember(previewDayType, goalConfig, profile, burnedCalories) {
        calculateDailyNutritionTargets(
            profile = profile,
            burnedCalories = burnedCalories,
            goalConfig = goalConfig,
            dayType = previewDayType
        )
    }
    val planDetail = remember(previewDayType, goalConfig.mode) {
        describeDayPlan(goalConfig.mode, previewDayType)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "设定 $dateStr 状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "点击任一选项可先查看营养吃法详情，点「确定」后生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 3 个选项卡片：训练日 / 休息日 / 放纵日
                    DayType.entries.forEach { dayType ->
                        val isSelected = previewDayType == dayType
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { previewDayType = dayType },
                            shape = RoundedCornerShape(16.dp),
                            border = if (isSelected) BorderStroke(1.8.dp, GreenPrimary) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    GreenPrimary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = dayType.badgeEmoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = dayType.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) GreenPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "已选中",
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // 所选模式的详情说明与今日具体上限数值预览
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "${previewDayType.badgeEmoji} ${previewDayType.displayName} 详情与今日限额",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // 今日算出来的具体热量与碳蛋脂目标
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "当前主模式：${goalConfig.mode.displayName}  →  目标热量 ${previewTargets.caloriesTarget} kcal",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "蛋白 ${ImageStorageUtils.formatOneDecimal(previewTargets.proteinTarget)}g",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BlueAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "脂肪 ${ImageStorageUtils.formatOneDecimal(previewTargets.fatTarget)}g",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OrangeWarning,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "碳水 ${ImageStorageUtils.formatOneDecimal(previewTargets.carbsTarget)}g",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PurpleAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "配比策略：$planDetail",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "营养原理与吃法：${previewDayType.summaryTip}",
                                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 17.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (showSkipButton) "暂不选择" else "取消")
                    }
                    Button(
                        onClick = { onConfirm(previewDayType) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
