package com.diettracker.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.diettracker.app.data.local.BackupDataUtils
import com.diettracker.app.data.local.DietDatabase
import com.diettracker.app.data.local.ImportResult
import com.diettracker.app.data.preferences.UserPreferencesManager
import com.diettracker.app.data.remote.OpenAIService
import com.diettracker.app.model.ApiConfig
import com.diettracker.app.model.AppearanceConfig
import com.diettracker.app.model.UserProfile
import com.diettracker.app.ui.theme.GreenPrimary
import com.diettracker.app.ui.theme.OrangeWarning
import com.diettracker.app.util.ImageStorageUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    db: DietDatabase,
    prefsManager: UserPreferencesManager,
    openAIService: OpenAIService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val initialProfile by prefsManager.userProfile.collectAsState()
    val initialApi by prefsManager.apiConfig.collectAsState()
    val initialAppearance by prefsManager.appearanceConfig.collectAsState()
    val currentGoalConfig by prefsManager.goalConfig.collectAsState()
    var showGoalModeDialog by remember { mutableStateOf(false) }

    var nickname by remember { mutableStateOf(initialProfile.nickname) }
    var avatarUri by remember { mutableStateOf(initialProfile.avatarUri) }
    var gender by remember { mutableStateOf(initialProfile.gender) }
    var age by remember { mutableStateOf(initialProfile.age.toString()) }
    var height by remember { mutableStateOf(initialProfile.height.toString()) }
    var weight by remember { mutableStateOf(initialProfile.weight.toString()) }

    var isDarkMode by remember { mutableStateOf(initialAppearance.isDarkMode) }
    var customBgUri by remember { mutableStateOf(initialAppearance.customBgUri) }
    var bgOpacity by remember { mutableStateOf(initialAppearance.bgOpacity) }

    var baseUrl by remember { mutableStateOf(initialApi.baseUrl) }
    var apiKey by remember { mutableStateOf(initialApi.apiKey) }
    var chatModel by remember { mutableStateOf(initialApi.chatModel) }
    var visionModel by remember { mutableStateOf(initialApi.visionModel) }

    // 模型检索状态
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelFetchStatus by remember { mutableStateOf<String?>(null) }
    var pickingModelFor by remember { mutableStateOf<String?>(null) } // "chat" or "vision"

    // 备份、恢复与隐私状态
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var importConfirmJson by remember { mutableStateOf<String?>(null) }
    var importResultDialog by remember { mutableStateOf<ImportResult?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    // 导出文件保存 Launcher
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            coroutineScope.launch {
                try {
                    val jsonStr = BackupDataUtils.exportBackupJson(db, prefsManager)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    isExporting = false
                    Toast.makeText(context, "数据备份已成功导出！", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    isExporting = false
                    Toast.makeText(context, "导出失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导入文件选择 Launcher
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { ins ->
                        ins.bufferedReader().readText()
                    }
                    if (!content.isNullOrBlank()) {
                        importConfirmJson = content
                    } else {
                        Toast.makeText(context, "所选备份文件内容为空", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "读取备份文件失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun fetchModels(openPickerTarget: String? = null) {
        isFetchingModels = true
        modelFetchStatus = null
        coroutineScope.launch {
            val result = openAIService.fetchAvailableModels(baseUrl, apiKey)
            isFetchingModels = false
            result.onSuccess { models ->
                availableModels = models
                modelFetchStatus = "已检索到 ${models.size} 个可用模型，点击右侧「选择」即可填入"
                if (openPickerTarget != null) {
                    pickingModelFor = openPickerTarget
                }
                Toast.makeText(context, "成功获取 ${models.size} 个模型！", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                modelFetchStatus = "检索失败：${err.message}"
                Toast.makeText(context, "检索失败: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 动态计算指标
    val currentHeight = height.toFloatOrNull() ?: 175f
    val currentWeight = weight.toFloatOrNull() ?: 68f
    val currentAge = age.toIntOrNull() ?: 25
    val previewProfile = UserProfile(
        nickname = nickname,
        avatarUri = avatarUri,
        gender = gender,
        age = currentAge,
        height = currentHeight,
        weight = currentWeight
    )

    // 相册选头像（复制到私有目录持久化保存）
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val savedUri = ImageStorageUtils.copyUriToInternalStorage(context, it, "avatar")
                if (savedUri != null) {
                    avatarUri = savedUri
                } else {
                    Toast.makeText(context, "读取头像图片失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 相册选自定义背景（复制到私有目录持久化保存）
    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val savedUri = ImageStorageUtils.copyUriToInternalStorage(context, it, "bg")
                if (savedUri != null) {
                    customBgUri = savedUri
                } else {
                    Toast.makeText(context, "读取背景图片失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置与个人资料", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 头像与昵称
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(3.dp, GreenPrimary, CircleShape)
                            .clickable { avatarPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUri.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUri,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Text(
                        text = "点击更换头像",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("用户昵称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. 身体数据与科学指标
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("身体数据与科学代谢", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 性别选择
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("性别：")
                            FilterChip(
                                selected = gender == "男",
                                onClick = { gender = "男" },
                                label = { Text("男") }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = gender == "女",
                                onClick = { gender = "女" },
                                label = { Text("女") }
                            )
                        }

                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it },
                            label = { Text("年龄") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = height,
                            onValueChange = { height = it },
                            label = { Text("身高 (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = { Text("体重 (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // 实时计算结果展示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BMI 指数", style = MaterialTheme.typography.labelSmall)
                            Text(
                                String.format("%.1f", previewProfile.bmi),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("静息代谢 (BMR)", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "${previewProfile.bmr} kcal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("建议维持消耗", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "${previewProfile.maintenanceCalories} kcal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("营养目标模式", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = currentGoalConfig.mode.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }
                        FilledTonalButton(onClick = { showGoalModeDialog = true }) {
                            Text("切换 / 设上限")
                        }
                    }
                }
            }

            // 3. 外观主题与自定义背景透明度
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("外观与壁纸个性化", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isDarkMode) "当前：极简纯黑主题" else "当前：极简纯白主题")
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { isDarkMode = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自定义背景壁纸")
                        OutlinedButton(onClick = { bgPickerLauncher.launch("image/*") }) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (customBgUri.isNullOrBlank()) "上传壁纸" else "更换壁纸")
                        }
                    }

                    if (!customBgUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("壁纸透明度: ${(bgOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { customBgUri = null }) {
                                Text("清除壁纸", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Slider(
                            value = bgOpacity,
                            onValueChange = { bgOpacity = it },
                            valueRange = 0f..1f
                        )
                    }
                }
            }

            // 4. OpenAI 格式 API 设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI 接口配置 (OpenAI 格式)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "支持 OpenAI、DeepSeek、通义千问、Kimi 等任何兼容端点",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API Base URL") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(GreenPrimary.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = GreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "本地安全承诺：你填写的 API Key 仅在当前设备本地私有空间密文保存。应用无后台中转服务器，绝不收集、上传或转发你的密钥信息。",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { fetchModels() },
                        enabled = !isFetchingModels,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在连接接口检索模型...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (availableModels.isEmpty()) "检索可用模型列表" else "重新检索可用模型 (${availableModels.size})")
                        }
                    }

                    if (!modelFetchStatus.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = modelFetchStatus!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (modelFetchStatus!!.startsWith("检索失败")) MaterialTheme.colorScheme.error else GreenPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatModel,
                            onValueChange = { chatModel = it },
                            label = { Text("文本模型 (饮食点评/文字估算)") },
                            placeholder = { Text("gpt-4o-mini 或 deepseek-chat") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = {
                                if (availableModels.isNotEmpty()) {
                                    pickingModelFor = "chat"
                                } else {
                                    fetchModels(openPickerTarget = "chat")
                                }
                            },
                            enabled = !isFetchingModels,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("选择")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = visionModel,
                            onValueChange = { visionModel = it },
                            label = { Text("视觉识图模型 (拍照识图/成分表)") },
                            placeholder = { Text("gpt-4o 或 qwen-vl-plus") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = {
                                if (availableModels.isNotEmpty()) {
                                    pickingModelFor = "vision"
                                } else {
                                    fetchModels(openPickerTarget = "vision")
                                }
                            },
                            enabled = !isFetchingModels,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("选择")
                        }
                    }
                }
            }

            // 数据备份与迁移（JSON）卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "📦", fontSize = 16.sp)
                        Text(
                            text = "数据备份与迁移 (JSON)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "将手机本地的饮食记录、体重历史、自写笔记与自定义食物库完整导出为标准 JSON 备份文件，换机、重装或定期归档轻松无忧。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val timeSuffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                exportBackupLauncher.launch("DietTracker_Backup_$timeSuffix.json")
                            },
                            enabled = !isExporting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("导出中...")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("导出备份")
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                importBackupLauncher.launch(arrayOf("application/json", "text/*"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("恢复备份")
                        }
                    }
                }
            }

            // 关于与隐私政策卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "ℹ️", fontSize = 16.sp)
                        Text(
                            text = "关于与隐私合规",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前版本",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "DietTracker v2.7 (Build 18)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("github_url", "https://github.com/rick3059945432-eng/DietTracker"))
                                Toast.makeText(context, "已复制 GitHub 开源地址", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "开源协议",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "MIT License (点击复制链接)",
                                fontSize = 12.sp,
                                color = GreenPrimary
                            )
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp), tint = GreenPrimary)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    OutlinedButton(
                        onClick = { showPrivacyDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("查看《隐私政策与免责声明》", color = GreenPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 底部保存按钮
            Button(
                onClick = {
                    val newProfile = UserProfile(
                        nickname = nickname.ifBlank { "自律伙伴" },
                        avatarUri = avatarUri,
                        gender = gender,
                        age = currentAge,
                        height = currentHeight,
                        weight = currentWeight
                    )
                    val newApi = ApiConfig(
                        baseUrl = baseUrl.ifBlank { "https://api.openai.com/v1" },
                        apiKey = apiKey,
                        chatModel = chatModel.ifBlank { "gpt-4o-mini" },
                        visionModel = visionModel.ifBlank { "gpt-4o" }
                    )
                    val newAppearance = AppearanceConfig(
                        isDarkMode = isDarkMode,
                        customBgUri = customBgUri,
                        bgOpacity = bgOpacity
                    )

                    prefsManager.saveUserProfile(newProfile)
                    prefsManager.saveApiConfig(newApi)
                    prefsManager.saveAppearanceConfig(newAppearance)

                    ImageStorageUtils.cleanupOldImages(context, "avatar", newProfile.avatarUri)
                    ImageStorageUtils.cleanupOldImages(context, "bg", newAppearance.customBgUri)

                    Toast.makeText(context, "设置保存成功！", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("保存全部设置", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (pickingModelFor != null) {
        val isChatTarget = pickingModelFor == "chat"
        ModelPickerDialog(
            title = if (isChatTarget) "选择文本模型" else "选择视觉识图模型",
            models = availableModels,
            currentSelection = if (isChatTarget) chatModel else visionModel,
            isRefreshing = isFetchingModels,
            onRefresh = { fetchModels(openPickerTarget = pickingModelFor) },
            onSelect = { selected ->
                if (isChatTarget) {
                    chatModel = selected
                } else {
                    visionModel = selected
                }
                pickingModelFor = null
            },
            onDismiss = { pickingModelFor = null }
        )
    }

    if (showGoalModeDialog) {
        GoalModeDialog(
            currentConfig = currentGoalConfig,
            profile = previewProfile,
            burnedCalories = 0,
            onDismiss = { showGoalModeDialog = false },
            onConfirm = { newConfig ->
                prefsManager.saveGoalConfig(newConfig)
                showGoalModeDialog = false
                Toast.makeText(context, "已应用「${newConfig.mode.displayName}」", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showPrivacyDialog) {
        PrivacyDisclaimerDialog(onDismiss = { showPrivacyDialog = false })
    }

    if (importConfirmJson != null) {
        AlertDialog(
            onDismissRequest = { importConfirmJson = null },
            title = { Text("确认恢复数据？", fontWeight = FontWeight.Bold) },
            text = { Text("从该备份文件导入将合并现有饮食记录、体重历史与食物库。确定继续吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        val json = importConfirmJson
                        importConfirmJson = null
                        if (json != null) {
                            coroutineScope.launch {
                                val result = BackupDataUtils.importBackupJson(json, db, prefsManager)
                                importResultDialog = result
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("确认导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { importConfirmJson = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (importResultDialog != null) {
        val result = importResultDialog!!
        AlertDialog(
            onDismissRequest = { importResultDialog = null },
            title = {
                Text(
                    text = if (result.isSuccess) "备份恢复成功" else "导入失败",
                    fontWeight = FontWeight.Bold,
                    color = if (result.isSuccess) GreenPrimary else MaterialTheme.colorScheme.error
                )
            },
            text = { Text(result.message) },
            confirmButton = {
                Button(onClick = { importResultDialog = null }) {
                    Text("好的")
                }
            }
        )
    }
}

@Composable
private fun ModelPickerDialog(
    title: String,
    models: List<String>,
    currentSelection: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var filterText by remember { mutableStateOf("") }
    val filtered = remember(models, filterText) {
        if (filterText.isBlank()) models
        else models.filter { it.contains(filterText.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$title (${filtered.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新模型列表")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("输入关键词筛选（如 deepseek / vl / 4o）", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { modelId ->
                        val isCurrent = modelId == currentSelection.trim()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(modelId) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) {
                                    GreenPrimary.copy(alpha = 0.15f)
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
                                Text(
                                    text = modelId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选中",
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
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
