package com.diettracker.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diettracker.app.data.local.ChatDao
import com.diettracker.app.data.local.WikiDao
import com.diettracker.app.data.remote.OpenAIService
import com.diettracker.app.model.*
import com.diettracker.app.ui.theme.GreenPrimary
import kotlinx.coroutines.launch

@Composable
fun AiCoachPage(
    dateStr: String,
    profile: UserProfile,
    meals: List<MealRecord>,
    burnedCalories: Int,
    targets: DailyNutritionTargets,
    apiConfig: ApiConfig,
    chatDao: ChatDao,
    wikiDao: WikiDao,
    openAIService: OpenAIService,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages by chatDao.getMessagesByDate(dateStr).collectAsState(initial = emptyList())
    var inputText by remember(dateStr) { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAnalysisConfirm by remember { mutableStateOf(false) }
    var savingToWikiArticle by remember { mutableStateOf<WikiArticle?>(null) }

    val totalCalories = meals.sumOf { it.calories }

    LaunchedEffect(messages.size, isSending) {
        val targetIndex = if (isSending) messages.size else messages.size - 1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    fun sendUserMessage(rawPrompt: String) {
        val text = rawPrompt.trim()
        if (text.isBlank() || isSending) return

        if (apiConfig.apiKey.isBlank()) {
            Toast.makeText(context, "请先点击左上角头像配置 API Key", Toast.LENGTH_LONG).show()
            onNavigateToSettings()
            return
        }

        val priorHistory = messages.toList()
        isSending = true
        coroutineScope.launch {
            chatDao.insertMessage(
                ChatMessage(
                    date = dateStr,
                    role = "user",
                    content = text
                )
            )
            val result = openAIService.chatWithCoach(
                dateStr = dateStr,
                profile = profile,
                meals = meals,
                burnedCalories = burnedCalories,
                targets = targets,
                historyMessages = priorHistory,
                userMessage = text,
                config = apiConfig
            )
            isSending = false
            result.onSuccess { reply ->
                chatDao.insertMessage(
                    ChatMessage(
                        date = dateStr,
                        role = "assistant",
                        content = reply
                    )
                )
            }.onFailure { err ->
                chatDao.insertMessage(
                    ChatMessage(
                        date = dateStr,
                        role = "assistant",
                        content = "回复失败：${err.message}\n请检查网络或设置中的 API Key / Base URL / 模型名是否正确。"
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 聊天消息流（无顶部状态条）
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            if (messages.isEmpty() && !isSending) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🤖 ${profile.nickname} 的私人营养教练",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "点击下方「✨ 一键分析」，或直接在输入框输入你的问题。\n\n" +
                                    "你可以随时追问，比如：\n" +
                                    "• 推荐晚上吃鱼，但我家里没有鱼怎么办？\n" +
                                    "• 晚上只剩鸡蛋和吐司，怎么凑够还差的蛋白质？\n" +
                                    "• 今天训练完好饿，还能加餐吗？\n\n" +
                                    "每天的对话都会按日期独立保存在本地，切换到历史日期即可回看当天记录。",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                if (isUser) {
                    // 用户气泡：右对齐
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 290.dp),
                            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                            colors = CardDefaults.cardColors(containerColor = GreenPrimary)
                        ) {
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                } else {
                    // AI 回复气泡：居中大卡片 + 下方操作行
                    val cleanContent = remember(msg.content) { OpenAIService.stripMarkdown(msg.content) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text(
                                    text = "🤖 AI 私教",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = cleanContent,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // 操作行：重试 / 复制 / 收藏
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 重试
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { if (!isSending) sendUserMessage(msg.content) }
                                    .height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "重试", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                    Text(text = "重试", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // 复制
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("AI Coach", cleanContent))
                                        Toast.makeText(context, "已复制回复", Toast.LENGTH_SHORT).show()
                                    }
                                    .height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                    Text(text = "复制", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // 收藏到小百科
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        val title = cleanContent.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(18) ?: "私教建议笔记"
                                        savingToWikiArticle = WikiArticle(title = title, category = "我的笔记", content = cleanContent, isBuiltin = false)
                                    }
                                    .height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "收藏", tint = GreenPrimary, modifier = Modifier.size(13.dp))
                                    Text(text = "收藏", fontSize = 11.sp, color = GreenPrimary)
                                }
                            }
                        }
                    }
                }
            }

            if (isSending) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = GreenPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "AI 私教正在结合今日饮食思考中...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 一键分析 + 清空对话 小胶囊行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 一键分析
            Button(
                onClick = { showAnalysisConfirm = true },
                enabled = !isSending,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "一键分析",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            if (messages.isNotEmpty()) {
                Spacer(modifier = Modifier.width(10.dp))
                // 清空对话
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "清空对话",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 底部输入框行：相机图标 + 空白输入框 + 发送按钮
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 相机/上传图标
                IconButton(
                    onClick = {
                        Toast.makeText(context, "图片上传功能即将上线", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "上传图片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // 极简空白输入框（无提示文字）
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(GreenPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 发送按钮
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isSending) GreenPrimary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            val toSend = inputText
                            inputText = ""
                            sendUserMessage(toSend)
                        },
                        enabled = inputText.isNotBlank() && !isSending,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (inputText.isNotBlank() && !isSending) Color.Black
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空 $dateStr 的对话？") },
            text = { Text("仅清空这一天的 AI 私教聊天记录，不会影响今天的饮食摄入记录和其他日期的对话。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            chatDao.clearMessagesForDate(dateStr)
                            showClearConfirm = false
                        }
                    }
                ) {
                    Text("确定清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    savingToWikiArticle?.let { draft ->
        WikiEditorDialog(
            initialArticle = draft,
            dialogTitle = "收藏私教回复到小百科",
            onDismiss = { savingToWikiArticle = null },
            onSave = { saved ->
                coroutineScope.launch {
                    wikiDao.insertArticle(saved)
                    savingToWikiArticle = null
                    Toast.makeText(context, "已收藏到「📖 小百科」！", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 一键分析确认弹窗
    if (showAnalysisConfirm) {
        AlertDialog(
            onDismissRequest = { showAnalysisConfirm = false },
            title = { Text("✨ 一键分析今日饮食") },
            text = {
                Text(
                    "AI 私教将综合分析今天的饮食摄入、热量缺口与碳蛋脂达标情况，并给出晚餐具体建议。\n\n" +
                    "点击「开始分析」即可发起对话（将消耗 API 额度）。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAnalysisConfirm = false
                        sendUserMessage("请帮我全面分析今天的饮食摄入、热量缺口和碳蛋脂达标情况，并结合我的目标给出接下来的饮食及晚餐具体建议。")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("开始分析", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnalysisConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
