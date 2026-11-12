package com.diettracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.diettracker.app.data.local.MealDao
import com.diettracker.app.model.DateCalendarInfo
import com.diettracker.app.model.MealRecord
import com.diettracker.app.model.MealType
import com.diettracker.app.ui.theme.GreenPrimary

@Composable
fun CopyFromHistoryDialog(
    targetDateStr: String,
    targetMealType: MealType,
    dateInfos: Map<String, DateCalendarInfo>,
    mealDao: MealDao,
    onConfirmCopy: (sourceDate: String, sourceMealType: MealType, sourceMeals: List<MealRecord>) -> Unit,
    onDismiss: () -> Unit
) {
    var pickedSourceDate by remember { mutableStateOf<String?>(null) }
    var sourceMeals by remember { mutableStateOf<List<MealRecord>>(emptyList()) }
    var isLoadingMeals by remember { mutableStateOf(false) }

    LaunchedEffect(pickedSourceDate) {
        val date = pickedSourceDate
        if (date != null) {
            isLoadingMeals = true
            sourceMeals = mealDao.getMealsByDateSync(date)
            isLoadingMeals = false
        }
    }

    if (pickedSourceDate == null) {
        CalendarDialog(
            selectedDateStr = targetDateStr,
            dateInfos = dateInfos,
            title = "选择要复制的历史日期",
            subtitle = "将把所选餐次覆盖填入 $targetDateStr 的${targetMealType.displayName}",
            onSelectDate = { date ->
                pickedSourceDate = date
            },
            onDismiss = onDismiss
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.84f),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "选择 $pickedSourceDate 的餐次",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "点击任意餐次，将覆盖填入当前 ${targetMealType.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { pickedSourceDate = null }) {
                            Text("换一天", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoadingMeals) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GreenPrimary)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MealType.entries.forEach { sourceType ->
                                val mealsForType = sourceMeals.filter { it.mealType == sourceType }
                                val totalCal = mealsForType.sumOf { it.calories }
                                val hasMeals = mealsForType.isNotEmpty()

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (hasMeals) {
                                                Modifier.clickable {
                                                    onConfirmCopy(pickedSourceDate!!, sourceType, mealsForType)
                                                }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (hasMeals) BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.45f)) else null,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (hasMeals) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = sourceType.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (hasMeals) "$totalCal kcal" else "无记录",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (hasMeals) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            if (hasMeals) {
                                                Text(
                                                    text = "点击覆盖复制",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GreenPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        if (hasMeals) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            mealsForType.forEach { item ->
                                                Text(
                                                    text = "• ${item.foodName}（${item.calories} kcal）",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
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
                        Text("取消")
                    }
                }
            }
        }
    }
}
