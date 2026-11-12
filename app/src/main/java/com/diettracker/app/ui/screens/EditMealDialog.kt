package com.diettracker.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.model.MealRecord
import com.diettracker.app.ui.theme.GreenPrimary
import com.diettracker.app.util.ImageStorageUtils
import kotlin.math.roundToInt

private data class ParsedFoodQuantity(
    val baseName: String,
    val quantity: Float,
    val unit: String
)

private fun parseFoodQuantity(foodName: String): ParsedFoodQuantity? {
    val regex = Regex("""^(.+?)\s*[(（](\d+(?:\.\d+)?)\s*(g|ml|个|份|克|毫升)[)）]$""")
    val match = regex.find(foodName.trim()) ?: return null
    val baseName = match.groupValues[1].trim()
    val qty = match.groupValues[2].toFloatOrNull() ?: return null
    val unit = match.groupValues[3]
    if (qty <= 0f) return null
    return ParsedFoodQuantity(baseName, qty, unit)
}

@Composable
fun EditMealDialog(
    record: MealRecord,
    onDismiss: () -> Unit,
    onSave: (MealRecord) -> Unit
) {
    val context = LocalContext.current
    val parsedQty = remember(record.id, record.foodName) { parseFoodQuantity(record.foodName) }

    var foodName by remember(record.id) { mutableStateOf(record.foodName) }
    var calories by remember(record.id) { mutableStateOf(record.calories.toString()) }
    var protein by remember(record.id) { mutableStateOf(record.protein.toString()) }
    var fat by remember(record.id) { mutableStateOf(record.fat.toString()) }
    var carbs by remember(record.id) { mutableStateOf(record.carbs.toString()) }
    var note by remember(record.id) { mutableStateOf(record.note) }

    var quantityText by remember(record.id) {
        mutableStateOf(parsedQty?.let { ImageStorageUtils.formatOneDecimal(it.quantity) } ?: "1")
    }

    fun applyScaleRatio(ratio: Float, updatedName: String? = null) {
        if (updatedName != null) {
            foodName = updatedName
        }
        calories = (record.calories * ratio).roundToInt().toString()
        protein = ImageStorageUtils.formatOneDecimal(record.protein * ratio)
        fat = ImageStorageUtils.formatOneDecimal(record.fat * ratio)
        carbs = ImageStorageUtils.formatOneDecimal(record.carbs * ratio)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "编辑${record.mealType.displayName}记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = record.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "仅修改本条独立记录，不会改动其他餐次或常用食物库",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 数量 / 倍率等比调整卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = GreenPrimary.copy(alpha = 0.10f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (parsedQty != null) {
                                Text(
                                    text = "按数量等比调整（原记录：${ImageStorageUtils.formatOneDecimal(parsedQty.quantity)}${parsedQty.unit}）",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = quantityText,
                                    onValueChange = { newText ->
                                        quantityText = newText
                                        val newQty = newText.toFloatOrNull()
                                        if (newQty != null && newQty >= 0f) {
                                            val ratio = newQty / parsedQty.quantity
                                            val qtyLabel = ImageStorageUtils.formatOneDecimal(newQty)
                                            applyScaleRatio(
                                                ratio = ratio,
                                                updatedName = "${parsedQty.baseName} (${qtyLabel}${parsedQty.unit})"
                                            )
                                        }
                                    },
                                    label = { Text("修改摄入数量 (${parsedQty.unit})") },
                                    suffix = { Text(parsedQty.unit) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = "按倍率缩放营养素（可选，也可直接在下方手改）",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(0.5f to "×0.5", 1f to "×1", 1.5f to "×1.5", 2f to "×2").forEach { (factor, label) ->
                                        FilterChip(
                                            selected = quantityText.toFloatOrNull() == factor,
                                            onClick = {
                                                quantityText = ImageStorageUtils.formatOneDecimal(factor)
                                                applyScaleRatio(factor)
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = quantityText,
                                    onValueChange = { newText ->
                                        quantityText = newText
                                        val factor = newText.toFloatOrNull()
                                        if (factor != null && factor >= 0f) {
                                            applyScaleRatio(factor)
                                        }
                                    },
                                    label = { Text("自定义缩放倍率（1 为原数据）") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "各项数值微调：",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = foodName,
                        onValueChange = { foodName = it },
                        label = { Text("食物名称") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = calories,
                            onValueChange = { calories = it },
                            label = { Text("热量 (kcal)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val kj = calories.toFloatOrNull()
                                if (kj != null && kj > 0f) {
                                    val kcal = ImageStorageUtils.kjToKcal(kj)
                                    calories = kcal.toString()
                                    Toast.makeText(context, "已将 ${kj.toInt()} kJ 换算为 $kcal kcal", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "请先在左侧输入千焦数字", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("kJ→kcal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = protein,
                            onValueChange = { protein = it },
                            label = { Text("蛋白质(g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fat,
                            onValueChange = { fat = it },
                            label = { Text("脂肪(g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = carbs,
                            onValueChange = { carbs = it },
                            label = { Text("碳水(g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注说明（可选）") },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            if (foodName.isBlank()) {
                                Toast.makeText(context, "食物名称不能为空", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSave(
                                record.copy(
                                    foodName = foodName.trim(),
                                    calories = calories.toIntOrNull() ?: 0,
                                    protein = protein.toFloatOrNull() ?: 0f,
                                    fat = fat.toFloatOrNull() ?: 0f,
                                    carbs = carbs.toFloatOrNull() ?: 0f,
                                    note = note.trim()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存修改")
                    }
                }
            }
        }
    }
}
