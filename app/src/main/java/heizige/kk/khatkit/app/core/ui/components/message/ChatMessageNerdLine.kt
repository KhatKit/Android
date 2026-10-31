@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package heizige.kk.khatkit.app.core.ui.components.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.downloadForOffline
import heizige.kk.khatkit.app.core.ui.icons.schedule
import heizige.kk.khatkit.app.core.ui.icons.upload
import heizige.kk.khatkit.app.core.util.formatNumber
import heizige.kk.khatkit.app.core.util.toFixed
import heizige.kk.khatkit.uikit.mochi.MochiDialog
import java.time.Duration
import kotlinx.datetime.toJavaLocalDateTime

/**
 * UI-4：消息下方的统计行 —— 速度（tok/s）、耗时、上下文占用；点开弹 [MochiDialog] 看用量明细与占用条。
 *
 * 数据来源说明（本 fork 的 UIMessage 目前只有 usage/createdAt/finishedAt）：
 *  - 速度/耗时用 usage.completionTokens 与 createdAt→finishedAt 的时长算；
 *  - 上下文占用只有累计 usage（prompt+completion）；上游新增的 lastUsage/stepCount 还没进本 fork 的
 *    ai 模型（不在本任务 inScope），所以「步数」一行先不显示、占用按累计值标注。
 */
@Composable
fun ChatMessageNerdLine(
    message: UIMessage,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val settings = LocalSettings.current.displaySetting
    val usage = message.usage
    if (!settings.showTokenUsage || usage == null) return
    var showDetails by remember(message.id) { mutableStateOf(false) }
    val stats = remember(message) { message.nerdStats(usage) }

    ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
        CompositionLocalProvider(LocalContentColor provides color) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDetails = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                stats.tps?.let { tps ->
                    StatsItem(icon = { StatsIcon(bolt, "速度") }) {
                        Text(text = tps.toFixed(1) + " tok/s")
                    }
                }
                if (stats.durationMillis != null) {
                    StatsItem(icon = { StatsIcon(schedule, "耗时") }) {
                        Text(text = (stats.durationMillis / 1000f).toFixed(1) + "s")
                    }
                }
                StatsItem(icon = { StatsIcon(upload, "上下文") }) {
                    Text(text = "上下文 " + stats.contextTokens.formatNumber())
                    val cached = usage.cachedTokens
                    if (cached > 0) {
                        Text(text = " (" + cached.formatNumber() + " cached)")
                    }
                }
            }
        }
    }

    if (showDetails) {
        MochiDialog(
            show = true,
            onDismissRequest = { showDetails = false },
            title = "用量详情",
        ) {
            UsageDetails(stats)
        }
    }
}

@Composable
private fun UsageDetails(stats: NerdStats) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        ContextUsageBar(usage = stats.usage, modifier = Modifier.padding(bottom = 8.dp))
        UsageDetailRow("输入", stats.usage.promptTokens.formatNumber())
        if (stats.usage.cachedTokens > 0) {
            UsageDetailRow("缓存命中", stats.usage.cachedTokens.formatNumber())
        }
        UsageDetailRow("输出", stats.usage.completionTokens.formatNumber())
        UsageDetailRow("合计", stats.usage.totalTokens.formatNumber())
        stats.tps?.let { UsageDetailRow("速度", it.toFixed(1) + " tok/s") }
        stats.durationMillis?.let { UsageDetailRow("耗时", (it / 1000f).toFixed(1) + "s") }
        Text(
            text = "多轮工具调用时输入为累计值",
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 上下文占用条：缓存命中 / 未命中输入 / 输出三段，剩余是窗口空余（窗口未知时按占用铺满）。 */
@Composable
private fun ContextUsageBar(usage: TokenUsage, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    data class Segment(val label: String, val tokens: Int, val color: Color)
    val segments = listOf(
        Segment("缓存", usage.cachedTokens, colorScheme.primary),
        Segment("输入", (usage.promptTokens - usage.cachedTokens).coerceAtLeast(0), colorScheme.primary.copy(alpha = 0.45f)),
        Segment("输出", usage.completionTokens, colorScheme.tertiary),
    ).filter { it.tokens > 0 }
    val contextTokens = usage.promptTokens + usage.completionTokens
    val capacity = contextTokens.coerceAtLeast(1)
    val trackColor = colorScheme.surfaceContainerHighest

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "上下文占用",
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            Text(text = contextTokens.formatNumber() + " tokens")
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val gap = 2.dp.toPx()
            val radius = CornerRadius(size.height / 2)
            val available = size.width - gap * (segments.size - 1).coerceAtLeast(0)
            var x = 0f
            segments.forEach { segment ->
                if (x >= size.width) return@forEach
                val width = (segment.tokens.toFloat() / capacity * available).coerceAtLeast(size.height)
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(x, 0f),
                    size = Size(width.coerceAtMost(size.width - x), size.height),
                    cornerRadius = radius,
                )
                x += width + gap
            }
            if (x < size.width) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(size.width - x, size.height),
                    cornerRadius = radius,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            segments.forEach { segment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).background(segment.color, CircleShape))
                    Text(text = segment.label + " " + segment.tokens.formatNumber(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun UsageDetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, color = LocalContentColor.current.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
        Text(text = value)
    }
}

@Composable
private fun StatsIcon(imageVector: ImageVector, contentDescription: String) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(12.dp))
}

@Composable
fun StatsItem(icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        icon()
        content()
    }
}

private data class NerdStats(
    val usage: TokenUsage,
    val contextTokens: Int,
    val durationMillis: Long?,
    val tps: Float?,
)

private fun UIMessage.nerdStats(usage: TokenUsage): NerdStats {
    val durationMillis = finishedAt?.let {
        Duration.between(createdAt.toJavaLocalDateTime(), it.toJavaLocalDateTime()).toMillis().takeIf { ms -> ms > 0 }
    }
    val tps = durationMillis?.takeIf { it > 0 }?.let { ms -> usage.completionTokens.toFloat() / ms * 1000 }
    return NerdStats(
        usage = usage,
        contextTokens = usage.promptTokens + usage.completionTokens,
        durationMillis = durationMillis,
        tps = tps,
    )
}
