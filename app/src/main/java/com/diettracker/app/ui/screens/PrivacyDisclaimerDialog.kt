package com.diettracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.diettracker.app.ui.theme.GreenPrimary
import com.diettracker.app.ui.theme.OrangeWarning

@Composable
fun PrivacyDisclaimerDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
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
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "隐私政策与免责声明",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DisclaimerSection(
                        icon = Icons.Default.Lock,
                        title = "1. 本地存储与零数据收集承诺",
                        content = "DietTracker 是一款完全「本地优先、无后台服务器」的纯净工具应用：\n" +
                                "• 你的所有饮食记录、体重数据、生理指标、自写笔记均加密保存在当前手机本地数据库（SQLite）中；\n" +
                                "• 我们不搭建任何开发者远程服务器，不设用户账号体系，不采集任何设备识别码（IMEI/OAID）、通讯录、精确定位或用户个人隐私行为；\n" +
                                "• 你所导出的 JSON 备份文件完全由你自己掌控，可自由存储于手机本地或个人网盘。"
                    )

                    DisclaimerSection(
                        icon = Icons.Default.Security,
                        title = "2. 第三方 AI 接口自主配置说明",
                        content = "软件本身不内置任何收费大模型或中转代理：\n" +
                                "• AI 识图、营养分析与私教功能均基于「BYOK（自带密钥）」模式运行；\n" +
                                "• 你所填写的 API Key 和 Base URL 仅密文加密保存在本机的应用私有沙盒中；\n" +
                                "• 发起 AI 分析时，应用将直接且仅向你指定的 API 端点发送请求，绝不经过任何第三方服务器中转，开发者无权也无法获取你的密钥或对话内容。"
                    )

                    DisclaimerSection(
                        icon = Icons.Default.MedicalServices,
                        title = "3. 非医疗用途与健康免责声明",
                        content = "• 本应用内置的所有能量代谢计算（Mifflin-St Jeor 公式、EMA 体重平滑趋势、自适应 TDEE 能量反推算法）、食物营养数据库以及 AI 生成的建议，仅供个人健康生活习惯养成与日常记录参考；\n" +
                                "• 本应用非医疗器械，上述估算数据不构成任何临床诊断、医疗处方或专业医学治疗意见；\n" +
                                "• 如你有糖尿病、甲状腺疾病、心血管疾病、进食障碍、妊娠或哺乳等特殊生理状况，请在开展任何饮食与训练调整前务必遵从执业医师或临床注册营养师的指导。"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("我已阅读并了解", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DisclaimerSection(
    icon: ImageVector,
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = content,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
