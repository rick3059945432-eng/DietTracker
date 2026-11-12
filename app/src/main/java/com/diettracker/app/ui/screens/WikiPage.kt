package com.diettracker.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.data.local.WikiDao
import com.diettracker.app.data.remote.OpenAIService
import com.diettracker.app.model.ApiConfig
import com.diettracker.app.model.WIKI_CATEGORIES
import com.diettracker.app.model.WikiArticle
import com.diettracker.app.ui.theme.BlueAccent
import com.diettracker.app.ui.theme.GreenPrimary
import kotlinx.coroutines.launch

@Composable
fun WikiPage(
    wikiDao: WikiDao,
    apiConfig: ApiConfig,
    openAIService: OpenAIService,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val articles by wikiDao.getAllArticles().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    var isToolbarExpanded by rememberSaveable { mutableStateOf(true) }

    var readingArticle by remember { mutableStateOf<WikiArticle?>(null) }
    var editingArticle by remember { mutableStateOf<WikiArticle?>(null) }
    var showCreateEditor by remember { mutableStateOf(false) }
    var showAiTopicDialog by remember { mutableStateOf(false) }
    var aiTopicInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    val filteredArticles = remember(articles, searchQuery, selectedCategory) {
        articles.filter { article ->
            val catMatch = selectedCategory == "全部" || article.category == selectedCategory
            val q = searchQuery.trim()
            val queryMatch = q.isBlank() ||
                article.title.contains(q, ignoreCase = true) ||
                article.content.contains(q, ignoreCase = true)
            catMatch && queryMatch
        }
    }

    fun triggerAiWikiGeneration(topic: String) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isBlank()) {
            Toast.makeText(context, "请先输入你想了解的健身或营养主题", Toast.LENGTH_SHORT).show()
            return
        }
        if (apiConfig.apiKey.isBlank()) {
            Toast.makeText(context, "请先点击左上角头像配置 API Key", Toast.LENGTH_LONG).show()
            onNavigateToSettings()
            return
        }
        isGenerating = true
        coroutineScope.launch {
            val res = openAIService.generateWikiArticle(cleanTopic, apiConfig)
            isGenerating = false
            res.onSuccess { generated ->
                showAiTopicDialog = false
                editingArticle = generated
                showCreateEditor = true
                Toast.makeText(context, "AI 词条已生成，确认后即可保存到小百科！", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "生成失败: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部紧凑搜索 + 可收起分类/新建控制卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 极简空白单行搜索框（无教学提示字）
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(GreenPrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        innerTextField()
                                    }
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清空搜索",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 收起 / 展开切换按钮
                    Surface(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isToolbarExpanded = !isToolbarExpanded },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (isToolbarExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = if (isToolbarExpanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isToolbarExpanded) "收起" else "展开",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isToolbarExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(7.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (listOf("全部") + WIKI_CATEGORIES).forEach { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedCategory = cat },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    border = if (isSelected) {
                                        null
                                    } else {
                                        BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    }
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(7.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        triggerAiWikiGeneration(searchQuery)
                                    } else {
                                        aiTopicInput = ""
                                        showAiTopicDialog = true
                                    }
                                },
                                enabled = !isGenerating,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("正在生成...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AI 科普生成", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    editingArticle = null
                                    showCreateEditor = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("手写笔记", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 百科条目列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            if (filteredArticles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("没有找到相关百科内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "可以直接点上方「AI 科普生成」帮你查出并保存成卡片，或者「手写笔记」自己记录！",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(filteredArticles, key = { it.id }) { article ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { readingArticle = article },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (article.isBuiltin) BlueAccent.copy(alpha = 0.14f) else GreenPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = article.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (article.isBuiltin) BlueAccent else GreenPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (article.isBuiltin) "内置精华" else "我的收录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = article.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // 1. 阅读详情弹窗
    readingArticle?.let { article ->
        Dialog(onDismissRequest = { readingArticle = null }) {
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
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GreenPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = article.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Wiki", "${article.title}\n\n${article.content}")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "已复制内容", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    editingArticle = article
                                    readingArticle = null
                                    showCreateEditor = true
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        wikiDao.deleteArticle(article)
                                        readingArticle = null
                                        Toast.makeText(context, "已删除词条", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = article.content,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { readingArticle = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    // 2. AI 主题提问弹窗
    if (showAiTopicDialog) {
        AlertDialog(
            onDismissRequest = { if (!isGenerating) showAiTopicDialog = false },
            title = { Text("AI 科普生成百科卡片") },
            text = {
                Column {
                    Text(
                        text = "输入你想了解的营养或健身问题，AI 会整理成干净的小卡片供你永久保存：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = aiTopicInput,
                        onValueChange = { aiTopicInput = it },
                        label = { Text("例如：深蹲怎么保护膝盖 / 晚上饿了吃什么") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { triggerAiWikiGeneration(aiTopicInput) },
                    enabled = !isGenerating
                ) {
                    Text(if (isGenerating) "生成中..." else "立即生成")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAiTopicDialog = false },
                    enabled = !isGenerating
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 3. 新建 / 编辑百科卡片弹窗
    if (showCreateEditor) {
        WikiEditorDialog(
            initialArticle = editingArticle,
            onDismiss = { showCreateEditor = false },
            onSave = { saved ->
                coroutineScope.launch {
                    if (saved.id == 0L) {
                        wikiDao.insertArticle(saved)
                    } else {
                        wikiDao.updateArticle(saved)
                    }
                    showCreateEditor = false
                    Toast.makeText(context, "已保存到小百科！", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun WikiEditorDialog(
    initialArticle: WikiArticle?,
    dialogTitle: String? = null,
    onDismiss: () -> Unit,
    onSave: (WikiArticle) -> Unit
) {
    val context = LocalContext.current
    var title by remember(initialArticle) { mutableStateOf(initialArticle?.title ?: "") }
    var category by remember(initialArticle) {
        mutableStateOf(
            if (initialArticle != null && initialArticle.category in WIKI_CATEGORIES) {
                initialArticle.category
            } else {
                "我的笔记"
            }
        )
    }
    var content by remember(initialArticle) { mutableStateOf(initialArticle?.content ?: "") }

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
                    text = dialogTitle ?: if ((initialArticle?.id ?: 0L) == 0L) "保存到健身小百科" else "编辑百科词条",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("选择分类：", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WIKI_CATEGORIES.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("正文内容") },
                        minLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (title.isBlank() || content.isBlank()) {
                                Toast.makeText(context, "标题和内容不能为空", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSave(
                                WikiArticle(
                                    id = initialArticle?.id ?: 0L,
                                    title = title.trim(),
                                    category = category,
                                    content = content.trim(),
                                    isBuiltin = initialArticle?.isBuiltin ?: false,
                                    timestamp = initialArticle?.timestamp ?: System.currentTimeMillis()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存词条")
                    }
                }
            }
        }
    }
}
