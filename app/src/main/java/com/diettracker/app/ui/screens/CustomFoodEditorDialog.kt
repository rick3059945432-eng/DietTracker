package com.diettracker.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.data.remote.OpenAIService
import com.diettracker.app.model.ApiConfig
import com.diettracker.app.model.CustomFood
import com.diettracker.app.model.FoodUnitType
import com.diettracker.app.util.ImageStorageUtils
import kotlinx.coroutines.launch

@Composable
fun CustomFoodEditorDialog(
    initialFood: CustomFood? = null,
    apiConfig: ApiConfig,
    openAIService: OpenAIService,
    onDismiss: () -> Unit,
    onSave: (CustomFood) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialFood?.name ?: "") }
    var unitType by remember { mutableStateOf(initialFood?.unitType ?: FoodUnitType.PER_100G) }
    var calories by remember { mutableStateOf(initialFood?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(initialFood?.protein?.toString() ?: "") }
    var fat by remember { mutableStateOf(initialFood?.fat?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initialFood?.carbs?.toString() ?: "") }
    var scanHint by remember { mutableStateOf("") }

    var labelBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            labelBitmap = ImageStorageUtils.loadBitmapFromUri(context, it)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            labelBitmap = ImageStorageUtils.loadBitmapFromUri(context, tempCameraUri!!)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (initialFood == null) "新增常用食物" else "编辑常用食物",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "可拍包装营养成分表自动录入，或照着配料表手填一次永久复用",
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
                    // 拍摄/选取营养成分表快捷区
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uri = ImageStorageUtils.createTempCameraUri(context, "label_temp")
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("拍配料表")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("选成分表")
                        }
                    }

                    if (labelBitmap != null) {
                        Image(
                            bitmap = labelBitmap!!.asImageBitmap(),
                            contentDescription = "Nutrition Label Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Button(
                            onClick = {
                                isScanning = true
                                coroutineScope.launch {
                                    val base64 = ImageStorageUtils.bitmapToBase64(labelBitmap!!)
                                    val result = openAIService.analyzeNutritionLabelImage(base64, "image/jpeg", apiConfig)
                                    isScanning = false
                                    result.onSuccess { est ->
                                        if (name.isBlank() || est.foodName != "包装食品") {
                                            name = est.foodName
                                        }
                                        unitType = est.unitType
                                        calories = est.calories.toString()
                                        protein = est.protein.toString()
                                        fat = est.fat.toString()
                                        carbs = est.carbs.toString()
                                        scanHint = est.explanation
                                        Toast.makeText(context, "成分表识别完成，请核对下方数据！", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, "识表失败: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI 识别成分表 (自动千焦转千卡)")
                            }
                        }
                    }

                    if (scanHint.isNotBlank()) {
                        Text(
                            text = "识表说明：$scanHint",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider()

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("食物名称（如：即食鸡胸肉、水煮蛋）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "营养表基准计量单位：",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FoodUnitType.entries.forEach { option ->
                            FilterChip(
                                selected = unitType == option,
                                onClick = { unitType = option },
                                label = { Text(option.displayName) }
                            )
                        }
                    }

                    // 热量输入 + 千焦(kJ)一键换算按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = calories,
                            onValueChange = { calories = it },
                            label = { Text("基准热量 (kcal)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = {
                                val kj = calories.toFloatOrNull()
                                if (kj != null && kj > 0f) {
                                    val kcal = ImageStorageUtils.kjToKcal(kj)
                                    calories = kcal.toString()
                                    Toast.makeText(
                                        context,
                                        "已将 ${kj.toInt()} kJ 换算为 $kcal kcal",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(context, "请先在左侧输入包装上的千焦数字", Toast.LENGTH_SHORT).show()
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
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fat,
                            onValueChange = { fat = it },
                            label = { Text("脂肪(g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = carbs,
                            onValueChange = { carbs = it },
                            label = { Text("碳水(g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
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
                            if (name.isBlank()) {
                                Toast.makeText(context, "请填写食物名称", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSave(
                                CustomFood(
                                    id = initialFood?.id ?: 0,
                                    name = name.trim(),
                                    unitType = unitType,
                                    calories = calories.toIntOrNull() ?: 0,
                                    protein = protein.toFloatOrNull() ?: 0f,
                                    fat = fat.toFloatOrNull() ?: 0f,
                                    carbs = carbs.toFloatOrNull() ?: 0f,
                                    timestamp = initialFood?.timestamp ?: System.currentTimeMillis()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存常用食物")
                    }
                }
            }
        }
    }
}
