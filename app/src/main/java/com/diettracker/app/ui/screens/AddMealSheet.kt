package com.diettracker.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.data.local.CustomFoodDao
import com.diettracker.app.data.remote.OpenAIService
import com.diettracker.app.model.ApiConfig
import com.diettracker.app.model.CustomFood
import com.diettracker.app.model.MealRecord
import com.diettracker.app.model.MealType
import com.diettracker.app.ui.theme.GreenPrimary
import com.diettracker.app.util.ImageStorageUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AddMealDialog(
    mealType: MealType,
    dateString: String,
    apiConfig: ApiConfig,
    openAIService: OpenAIService,
    customFoodDao: CustomFoodDao,
    onCopyHistoryClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (MealRecord) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val customFoods by customFoodDao.getAllCustomFoods().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0: 常用食物, 1: 纯手动, 2: AI文字, 3: 识图AI
    val tabs = listOf("常用食物", "纯手动", "AI文字", "识图AI")

    // 表单状态
    var foodName by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    // 常用食物相关状态
    var searchQuery by remember { mutableStateOf("") }
    var selectedCustomFood by remember { mutableStateOf<CustomFood?>(null) }
    var consumedAmountText by remember { mutableStateOf("") }
    var showCustomFoodEditor by remember { mutableStateOf(false) }
    var editingCustomFood by remember { mutableStateOf<CustomFood?>(null) }

    // AI 相关输入与状态
    var aiTextInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // 相机拍照临时文件
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            imageBitmap = ImageStorageUtils.loadBitmapFromUri(context, it)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
            imageBitmap = ImageStorageUtils.loadBitmapFromUri(context, tempCameraUri!!)
        }
    }

    fun applyCustomFoodCalculation(food: CustomFood, amountText: String) {
        val qty = amountText.toFloatOrNull() ?: 0f
        val ratio = if (food.unitType.baseAmount > 0f) qty / food.unitType.baseAmount else 0f
        val formattedQty = ImageStorageUtils.formatOneDecimal(qty)
        foodName = if (qty > 0f) "${food.name} (${formattedQty}${food.unitType.unitSuffix})" else food.name
        calories = (food.calories * ratio).roundToInt().toString()
        protein = ImageStorageUtils.formatOneDecimal(food.protein * ratio)
        fat = ImageStorageUtils.formatOneDecimal(food.fat * ratio)
        carbs = ImageStorageUtils.formatOneDecimal(food.carbs * ratio)
        note = "来自常用食物：${food.name}（基准${food.unitType.displayName}）"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // 顶部标题与「同历史某次」快捷入口
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "记录${mealType.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateString,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onCopyHistoryClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("同历史某次", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 四种录入途径 Tab 切换
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 中间可滚动内容
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> { // 常用食物库
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("搜索常用食物...", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        editingCustomFood = null
                                        showCustomFoodEditor = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("新增", fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val filteredFoods = remember(customFoods, searchQuery) {
                                if (searchQuery.isBlank()) customFoods
                                else customFoods.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
                            }

                            if (filteredFoods.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (customFoods.isEmpty()) "还没有保存的常用食物" else "没有匹配的常用食物",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "点击右上角「+ 新增」，可照着配料表手填或直接拍成分表建库，以后点一下输数量就能直接加！",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredFoods.forEach { food ->
                                        val isSelected = selectedCustomFood?.id == food.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (!isSelected) {
                                                        selectedCustomFood = food
                                                        val defaultQty = if (food.unitType.baseAmount == 100f) "100" else "1"
                                                        consumedAmountText = defaultQty
                                                        applyCustomFoodCalculation(food, defaultQty)
                                                    }
                                                },
                                            shape = RoundedCornerShape(14.dp),
                                            border = if (isSelected) BorderStroke(1.5.dp, GreenPrimary) else null,
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    GreenPrimary.copy(alpha = 0.12f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                }
                                            )
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = food.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = food.unitType.displayName,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = GreenPrimary
                                                            )
                                                        }
                                                        Text(
                                                            text = "${food.calories} kcal | 蛋:${food.protein}g 脂:${food.fat}g 碳:${food.carbs}g",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Row {
                                                        IconButton(
                                                            onClick = {
                                                                editingCustomFood = food
                                                                showCustomFoodEditor = true
                                                            },
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Edit,
                                                                contentDescription = "编辑",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    customFoodDao.deleteCustomFood(food)
                                                                    if (selectedCustomFood?.id == food.id) {
                                                                        selectedCustomFood = null
                                                                        consumedAmountText = ""
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "删除",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                    }
                                                }

                                                // 选中后紧贴在当前食物卡片下方展开摄入数量输入框
                                                AnimatedVisibility(visible = isSelected) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                                    ) {
                                                        HorizontalDivider(color = GreenPrimary.copy(alpha = 0.25f))
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        OutlinedTextField(
                                                            value = consumedAmountText,
                                                            onValueChange = { newText ->
                                                                consumedAmountText = newText
                                                                applyCustomFoodCalculation(food, newText)
                                                            },
                                                            label = { Text("本次摄入数量 (${food.unitType.unitSuffix})") },
                                                            suffix = { Text(food.unitType.unitSuffix) },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = "本次计入：${calories.ifBlank { "0" }} kcal | 蛋:${protein.ifBlank { "0" }}g 脂:${fat.ifBlank { "0" }}g 碳:${carbs.ifBlank { "0" }}g",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = GreenPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> { // 纯手填
                            Text(
                                text = "直接填写食物名称和各项营养克数（支持右侧千焦一键转千卡）：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        2 -> { // AI 文字估算
                            OutlinedTextField(
                                value = aiTextInput,
                                onValueChange = { aiTextInput = it },
                                label = { Text("输入食物与分量（如：两片全麦吐司+煎蛋）") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (aiTextInput.isBlank()) {
                                        Toast.makeText(context, "请先输入食物描述！", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoading = true
                                    coroutineScope.launch {
                                        val result = openAIService.estimateFoodNutrition(aiTextInput, apiConfig)
                                        isLoading = false
                                        result.onSuccess { estimate ->
                                            foodName = estimate.foodName
                                            calories = estimate.calories.toString()
                                            protein = estimate.protein.toString()
                                            fat = estimate.fat.toString()
                                            carbs = estimate.carbs.toString()
                                            note = estimate.explanation
                                            Toast.makeText(context, "AI 估算成功，已填入下方！", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "估算失败: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AI 智能分析营养素")
                                }
                            }
                        }

                        3 -> { // 拍照/识图 AI
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val uri = ImageStorageUtils.createTempCameraUri(context, "camera_temp")
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("现场拍照")
                                }
                                OutlinedButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("相册选取")
                                }
                            }

                            if (imageBitmap != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Image(
                                    bitmap = imageBitmap!!.asImageBitmap(),
                                    contentDescription = "Food Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        isLoading = true
                                        coroutineScope.launch {
                                            val base64 = ImageStorageUtils.bitmapToBase64(imageBitmap!!)
                                            val result = openAIService.analyzeFoodImage(base64, "image/jpeg", apiConfig)
                                            isLoading = false
                                            result.onSuccess { estimate ->
                                                foodName = estimate.foodName
                                                calories = estimate.calories.toString()
                                                protein = estimate.protein.toString()
                                                fat = estimate.fat.toString()
                                                carbs = estimate.carbs.toString()
                                                note = estimate.explanation
                                                Toast.makeText(context, "AI 识图分析成功，已填入下方！", Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, "识图失败: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isLoading
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI 视觉识别热量")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "营养素确认（可手动微调）：",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = foodName,
                        onValueChange = { foodName = it },
                        label = { Text("食物名称") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = calories,
                            onValueChange = { calories = it },
                            label = { Text("总热量 (kcal)") },
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

                    Spacer(modifier = Modifier.height(6.dp))
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

                    if (note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "说明：$note",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 底部操作按钮
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
                            val calVal = calories.toIntOrNull() ?: 0
                            val pVal = protein.toFloatOrNull() ?: 0f
                            val fVal = fat.toFloatOrNull() ?: 0f
                            val cVal = carbs.toFloatOrNull() ?: 0f

                            onSave(
                                MealRecord(
                                    date = dateString,
                                    mealType = mealType,
                                    foodName = foodName,
                                    calories = calVal,
                                    protein = pVal,
                                    fat = fVal,
                                    carbs = cVal,
                                    note = note,
                                    imageUri = selectedImageUri?.toString()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存记录")
                    }
                }
            }
        }
    }

    if (showCustomFoodEditor) {
        CustomFoodEditorDialog(
            initialFood = editingCustomFood,
            apiConfig = apiConfig,
            openAIService = openAIService,
            onDismiss = { showCustomFoodEditor = false },
            onSave = { savedFood ->
                coroutineScope.launch {
                    if (savedFood.id == 0L) {
                        val newId = customFoodDao.insertCustomFood(savedFood)
                        val inserted = savedFood.copy(id = newId)
                        selectedCustomFood = inserted
                        val defaultQty = if (inserted.unitType.baseAmount == 100f) "100" else "1"
                        consumedAmountText = defaultQty
                        applyCustomFoodCalculation(inserted, defaultQty)
                    } else {
                        customFoodDao.updateCustomFood(savedFood)
                        if (selectedCustomFood?.id == savedFood.id) {
                            selectedCustomFood = savedFood
                            applyCustomFoodCalculation(savedFood, consumedAmountText)
                        }
                    }
                    showCustomFoodEditor = false
                    Toast.makeText(context, "已保存到常用食物库！", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
