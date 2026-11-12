package com.diettracker.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.diettracker.app.model.DailyNutritionTargets
import com.diettracker.app.model.MealRecord
import com.diettracker.app.model.MealType
import com.diettracker.app.ui.theme.*
import com.diettracker.app.util.ImageStorageUtils

// 支持自定义壁纸和透明度滑块的背景脚手架
@Composable
fun CustomBackgroundScaffold(
    isDarkMode: Boolean,
    customBgUri: String?,
    bgOpacity: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val baseBgColor = if (isDarkMode) Color(0xFF0D0D0D) else Color(0xFFF9FAFB)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        // 自定义壁纸层，受 bgOpacity 控制
        if (!customBgUri.isNullOrBlank()) {
            AsyncImage(
                model = customBgUri,
                contentDescription = "Custom Background",
                contentScale = ContentScale.Crop,
                alpha = bgOpacity.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize()
            )
        }

        // 内容层
        content()
    }
}

// 顶部问候与名言横幅（包含头像入口）
@Composable
fun QuoteBanner(
    nickname: String,
    avatarUri: String?,
    quoteText: String,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像（点击进入设置）
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(2.dp, GreenPrimary, CircleShape)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = "User Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default Avatar",
                        tint = GreenPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nickname,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(点击头像设置)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = quoteText,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 热量与碳蛋脂四圆环主仪表盘
@Composable
fun CalorieDashboardCard(
    currentCalories: Int,
    baseMaintenanceCalories: Int,
    burnedCalories: Int,
    targets: DailyNutritionTargets,
    protein: Float,
    fat: Float,
    carbs: Float,
    onSelectModeClick: () -> Unit,
    onSelectDayTypeClick: () -> Unit,
    onEditBurnedCalories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetCal = targets.caloriesTarget.coerceAtLeast(1)
    val remaining = targetCal - currentCalories
    val progress = (currentCalories.toFloat() / targetCal).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "CalorieProgress")
    val isOverCal = remaining < 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                        text = "今日热量预算",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 快捷主模式切换胶囊
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GreenPrimary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectModeClick() }
                        ) {
                            Text(
                                text = "${targets.mode.shortBadge} ▾",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // 快捷大小天（训练日/休息日/放纵日）切换胶囊
                        val dayType = targets.dayType
                        val dayBadgeText = if (dayType != null) {
                            "${dayType.badgeEmoji} ${dayType.shortLabel} ▾"
                        } else {
                            "选训练/休息日 ▾"
                        }
                        val dayBadgeBg = if (dayType != null) {
                            BlueAccent.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        val dayBadgeColor = if (dayType != null) {
                            BlueAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = dayBadgeBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectDayTypeClick() }
                        ) {
                            Text(
                                text = dayBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = dayBadgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "目标 $targetCal kcal（基代 $baseMaintenanceCalories + 运动 $burnedCalories）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 修改运动消耗快捷按钮
                FilledTonalButton(
                    onClick = onEditBurnedCalories,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "运动 +$burnedCalories",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 热量大圆环
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(128.dp)) {
                    val strokeWidth = 13.dp.toPx()
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.2f),
                        style = Stroke(width = strokeWidth)
                    )
                    if (isOverCal) {
                        drawArc(
                            color = RedDanger,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    } else {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(GreenPrimary, BlueAccent, GreenSecondary)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$currentCalories",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 27.sp, fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (remaining >= 0) "还可摄入 $remaining kcal" else "已超标 ${-remaining} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remaining >= 0) GreenPrimary else RedDanger,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // 碳水、蛋白质、脂肪 三大营养素圆环
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroRing(
                    name = "蛋白质",
                    currentGrams = protein,
                    targetGrams = targets.proteinTarget,
                    color = BlueAccent
                )
                MacroRing(
                    name = "脂肪",
                    currentGrams = fat,
                    targetGrams = targets.fatTarget,
                    color = OrangeWarning
                )
                MacroRing(
                    name = "碳水",
                    currentGrams = carbs,
                    targetGrams = targets.carbsTarget,
                    color = PurpleAccent
                )
            }
        }
    }
}

@Composable
fun MacroRing(
    name: String,
    currentGrams: Float,
    targetGrams: Float,
    color: Color
) {
    val safeTarget = targetGrams.coerceAtLeast(1f)
    val rawRatio = currentGrams / safeTarget
    val progress = rawRatio.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "MacroRing_$name")
    val isOver = currentGrams > safeTarget
    val ringColor = if (isOver) RedDanger else color

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(76.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 7.dp.toPx()
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.18f),
                    style = Stroke(width = strokeWidth)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${ImageStorageUtils.formatOneDecimal(currentGrams)}g",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = if (isOver) RedDanger else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (isOver) {
                "超 ${ImageStorageUtils.formatOneDecimal(currentGrams - safeTarget)}g"
            } else {
                "/ ${ImageStorageUtils.formatOneDecimal(safeTarget)}g"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isOver) RedDanger else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 每餐卡片
@Composable
fun MealSectionCard(
    mealType: MealType,
    records: List<MealRecord>,
    onAddClick: () -> Unit,
    onCopyHistoryClick: () -> Unit,
    onEditRecord: (MealRecord) -> Unit,
    onDeleteRecord: (MealRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCal = records.sumOf { it.calories }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mealType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$totalCal kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = GreenPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = GreenPrimary.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onCopyHistoryClick() }
                    ) {
                        Text(
                            text = "同历史",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add ${mealType.displayName}",
                            tint = GreenPrimary
                        )
                    }
                }
            }

            if (records.isEmpty()) {
                Text(
                    text = "暂无记录，点击右上角 + 记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                records.forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEditRecord(record) }
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.foodName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${record.calories} kcal | 蛋:${record.protein}g 脂:${record.fat}g 碳:${record.carbs}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onDeleteRecord(record) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
