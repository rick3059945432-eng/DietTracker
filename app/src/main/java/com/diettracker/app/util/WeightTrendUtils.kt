package com.diettracker.app.util

import com.diettracker.app.model.WeightRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 体重趋势计算与分析工具（纯函数）
 */
object WeightTrendUtils {

    const val EMA_ALPHA = 0.15f // 指数移动平均平滑系数

    data class TrendPoint(
        val record: WeightRecord,
        val trendWeight: Float // kg, 保留1位或2位小数的平滑趋势体重
    )

    data class ComfortFeedback(
        val title: String,
        val message: String,
        val level: FeedbackLevel
    )

    enum class FeedbackLevel {
        ENCOURAGING, // 正向鼓励
        REASSURING,  // 温和安抚（破除单日焦虑）
        STABLE       // 平稳正常
    }

    /**
     * 计算指数移动平均 (EMA) 趋势体重
     * Trend_t = alpha * Weight_t + (1 - alpha) * Trend_{t-1}
     *
     * 边界处理：
     * 1. 首条记录的 Trend_0 = Weight_0
     * 2. 数据先按日期升序排序
     * 3. 支持漏记天数（按有效记录序列自然递推，不填充 0）
     */
    fun computeEMA(records: List<WeightRecord>, alpha: Float = EMA_ALPHA): List<TrendPoint> {
        if (records.isEmpty()) return emptyList()

        val sorted = records.sortedBy { it.date }
        val result = ArrayList<TrendPoint>(sorted.size)

        var currentTrend = sorted[0].weight
        result.add(TrendPoint(record = sorted[0], trendWeight = currentTrend))

        for (i in 1 until sorted.size) {
            val raw = sorted[i].weight
            currentTrend = alpha * raw + (1f - alpha) * currentTrend
            result.add(TrendPoint(record = sorted[i], trendWeight = currentTrend))
        }

        return result
    }

    /**
     * 计算单日偏差值：rawWeight - trendWeight
     * 正值表示高于趋势线，负值表示低于趋势线
     */
    fun calculateDailyDeviation(rawWeight: Float, trendWeight: Float): Float {
        return rawWeight - trendWeight
    }

    /**
     * 计算最新趋势体重与大约 7 天前趋势体重的差值
     * 差值 = 最新趋势 - 7天前趋势 (负数表示下降)
     */
    fun calculateWeeklyTrendDelta(trendPoints: List<TrendPoint>): Float {
        if (trendPoints.size < 2) return 0f

        val latest = trendPoints.last()
        val latestDate = parseDate(latest.record.date) ?: return 0f

        // 寻找 7 天前（时间差约 7 天，即 5~9 天内，或小于等于 7 天前中最靠近 7 天的点）
        var targetPastPoint: TrendPoint? = null
        var bestDayDiff = Long.MAX_VALUE

        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L

        for (p in trendPoints) {
            if (p == latest) continue
            val d = parseDate(p.record.date) ?: continue
            val diffMs = latestDate.time - d.time
            if (diffMs > 0) {
                val distanceTo7Days = abs(diffMs - sevenDaysMs)
                if (distanceTo7Days < bestDayDiff) {
                    bestDayDiff = distanceTo7Days
                    targetPastPoint = p
                }
            }
        }

        // 如果找不到相隔多天的点，就使用第一条记录对比
        val pastPoint = targetPastPoint ?: trendPoints.first()
        return latest.trendWeight - pastPoint.trendWeight
    }

    /**
     * 根据偏差、7日趋势以及当日状态标签生成客观、温和的智能安抚卡片文案（严禁制造焦虑）
     */
    fun generateComfortFeedback(
        record: WeightRecord?,
        trendWeight: Float?,
        weeklyTrendDelta: Float,
        totalRecordsCount: Int
    ): ComfortFeedback {
        if (record == null || trendWeight == null) {
            return ComfortFeedback(
                title = "体重新篇章",
                message = "坚持晨起空腹称重，让指数移动平均（EMA）帮你过滤日常水分干扰，看见真实的身体变化走向。",
                level = FeedbackLevel.STABLE
            )
        }

        if (totalRecordsCount <= 2) {
            return ComfortFeedback(
                title = "趋势正在建立中",
                message = "人体水分、排便及肌糖原储备每天都会引起 0.5~2 kg 的正常波动。持续记录数天后，平滑趋势线会越来越精准！",
                level = FeedbackLevel.STABLE
            )
        }

        val deviation = calculateDailyDeviation(record.weight, trendWeight)
        val tags = record.tags

        val waterRetentionTags = listOf("高盐/聚餐", "大重量训练", "水肿/生理波动", "熬夜/睡眠差", "消化不佳")
        val hasWaterRetentionTag = tags.any { it in waterRetentionTags }

        // 场景 1: 体重偏高且有导致滞水的标签
        if (deviation >= 0.2f && hasWaterRetentionTag) {
            val matchedTags = tags.filter { it in waterRetentionTags }.joinToString("、")
            val diffStr = String.format(Locale.getDefault(), "%.1f", deviation)
            val trendDesc = if (weeklyTrendDelta < -0.1f) {
                "当前 7 日整体趋势仍在平稳走低，继续保持节奏就好！"
            } else {
                "不必介意单日数字，保持多喝水、清淡作息即可自然代谢。"
            }
            return ComfortFeedback(
                title = "生理正常波动 🌊",
                message = "今日原始体重偏高 ${diffStr} kg。结合你的【$matchedTags】标签来看，大概率是水分滞留或肌糖原超量充填，属于身体应对外界的正常生理反应。$trendDesc",
                level = FeedbackLevel.REASSURING
            )
        }

        // 场景 2: 体重偏高，但 7 日趋势呈现健康下降
        if (deviation >= 0.2f && weeklyTrendDelta <= -0.2f) {
            val deltaStr = String.format(Locale.getDefault(), "%.1f", abs(weeklyTrendDelta))
            val diffStr = String.format(Locale.getDefault(), "%.1f", deviation)
            return ComfortFeedback(
                title = "大趋势依然向下 📉",
                message = "单日小幅反弹 ${diffStr} kg 非常普遍。你的 7 日平滑趋势体重实际上已经累计稳步下降了 ${deltaStr} kg！单日波动就如海面波浪，底层的退潮趋势才是真相，继续按原计划饮食训练。",
                level = FeedbackLevel.REASSURING
            )
        }

        // 场景 3: 7 日趋势稳步下降 (减脂期正向反馈)
        if (weeklyTrendDelta <= -0.3f) {
            val deltaStr = String.format(Locale.getDefault(), "%.1f", abs(weeklyTrendDelta))
            return ComfortFeedback(
                title = "节奏良好，稳步推进 ✨",
                message = "近 7 日趋势体重稳步走低 ${deltaStr} kg。这证明你当前的饮食结构与训练安排非常合理，既给肌肉留足了营养，又保持了健康缺口，给身体一点时间，成果自然显现。",
                level = FeedbackLevel.ENCOURAGING
            )
        }

        // 场景 4: 今日体重出现明显回落（新低）
        if (deviation <= -0.4f) {
            val diffStr = String.format(Locale.getDefault(), "%.1f", abs(deviation))
            return ComfortFeedback(
                title = "阶段新低，平稳收官 🎯",
                message = "今日体重比平滑趋势线低了 ${diffStr} kg。身体减脂往往呈阶梯式释放，请继续保持规律作息和蛋白质摄入，不要因为降了就盲目多吃或过度少吃。",
                level = FeedbackLevel.ENCOURAGING
            )
        }

        // 场景 5: 波动很小，稳定平衡
        return ComfortFeedback(
            title = "状态平稳，自律如常 ⚖️",
            message = "今日体重与平滑趋势线紧密贴合（偏差仅 ${String.format(Locale.getDefault(), "%.1f", abs(deviation))} kg），体内代谢系统运转平稳，继续按今天设定的营养目标稳扎稳打。",
            level = FeedbackLevel.STABLE
        )
    }

    private fun parseDate(dateStr: String): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }
}
