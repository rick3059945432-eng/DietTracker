package com.diettracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.model.DietGoalMode
import com.diettracker.app.model.GoalConfig
import com.diettracker.app.model.UserProfile
import com.diettracker.app.model.calculateDailyNutritionTargets
import com.diettracker.app.ui.theme.GreenPrimary
import com.diettracker.app.util.ImageStorageUtils

@Composable
fun GoalModeDialog(
    currentConfig: GoalConfig,
    profile: UserProfile,
    burnedCalories: Int,
    onDismiss: () -> Unit,
    onConfirm: (GoalConfig) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentConfig.mode) }
    var customCalText by remember { mutableStateOf(currentConfig.customCalories.toString()) }
    var customProteinText by remember { mutableStateOf(ImageStorageUtils.formatOneDecimal(currentConfig.customProtein)) }
    var customFatText by remember { mutableStateOf(ImageStorageUtils.formatOneDecimal(currentConfig.customFat)) }
    var customCarbsText by remember { mutableStateOf(ImageStorageUtils.formatOneDecimal(currentConfig.customCarbs)) }

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
                    text = "选择饮食与营养模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "不同阶段的热量与碳蛋脂上限不同，切换后圆环与 AI 建议同步生效",
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
                    DietGoalMode.entries.forEach { mode ->
                        val isSelected = selectedMode == mode
                        val previewTargets = calculateDailyNutritionTargets(
                            profile = profile,
                            burnedCalories = burnedCalories,
                            goalConfig = GoalConfig(
                                mode = mode,
                                customCalories = customCalText.toIntOrNull() ?: currentConfig.customCalories,
                                customProtein = customProteinText.toFloatOrNull() ?: currentConfig.customProtein,
                                customFat = customFatText.toFloatOrNull() ?: currentConfig.customFat,
                                customCarbs = customCarbsText.toFloatOrNull() ?: currentConfig.customCarbs
                            )
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = mode },
                            shape = RoundedCornerShape(16.dp),
                            border = if (isSelected) BorderStroke(1.8.dp, GreenPrimary) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    GreenPrimary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) GreenPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "已选",
                                            tint = GreenPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (mode != DietGoalMode.CUSTOM) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "今日上限：${previewTargets.caloriesTarget} kcal | " +
                                            "蛋 ${ImageStorageUtils.formatOneDecimal(previewTargets.proteinTarget)}g · " +
                                            "脂 ${ImageStorageUtils.formatOneDecimal(previewTargets.fatTarget)}g · " +
                                            "碳 ${ImageStorageUtils.formatOneDecimal(previewTargets.carbsTarget)}g",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 选择“自定义上限”时展开手填参数区
                    if (selectedMode == DietGoalMode.CUSTOM) {
                        HorizontalDivider()
                        Text(
                            text = "自定义每日摄入上限：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )
                        OutlinedTextField(
                            value = customCalText,
                            onValueChange = { customCalText = it },
                            label = { Text("每日热量上限 (kcal)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = customProteinText,
                                onValueChange = { customProteinText = it },
                                label = { Text("蛋白(g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customFatText,
                                onValueChange = { customFatText = it },
                                label = { Text("脂肪(g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customCarbsText,
                                onValueChange = { customCarbsText = it },
                                label = { Text("碳水(g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
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
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            onConfirm(
                                GoalConfig(
                                    mode = selectedMode,
                                    customCalories = (customCalText.toIntOrNull() ?: currentConfig.customCalories).coerceAtLeast(100),
                                    customProtein = (customProteinText.toFloatOrNull() ?: currentConfig.customProtein).coerceAtLeast(1f),
                                    customFat = (customFatText.toFloatOrNull() ?: currentConfig.customFat).coerceAtLeast(1f),
                                    customCarbs = (customCarbsText.toFloatOrNull() ?: currentConfig.customCarbs).coerceAtLeast(1f)
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("应用模式")
                    }
                }
            }
        }
    }
}
