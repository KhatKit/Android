package heizige.kk.khatkit.app.feature.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.dao.UsageBySource
import heizige.kk.khatkit.app.core.data.db.dao.UsageTrendPoint
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.icons.barChart
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.memory
import heizige.kk.khatkit.app.core.ui.icons.rocketLaunch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/**
 * 统计页（Mochi / iOS 26 分组列表）。
 *
 * 骨架是 [MochiLazyLargeTitleScaffold]，正文全部落在 [MochiListSection] 分组卡片里：
 *  1. 概览：两个主数字（会话 / 消息）用 Mochi 计数器滚动，下面 2x2 收起 token 与启动次数；
 *  2. 热力：近 52 周的聊天热力，连续圆角小格 + 5 档主色深浅，底部报连续天数；
 *  3. 用量：Mochi 分段选择器（7/30/90/全部）+ 面积趋势图 + 模型 / 来源排行行；
 *  4. 任务路由：按任务聚合的模型路由次数，都是标准 [MochiListRow]。
 *
 * 主题色、字阶、圆角全部取 MochiTheme；页面底色、分组卡片、行高与分隔线由 Mochi 脚手架
 * 和 MochiListSection 统一给，页面自己不再画卡片底。
 */
@Composable
fun StatsPage(vm: StatsVM = hiltViewModel()) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val usageRange by vm.range.collectAsStateWithLifecycle()

    // 路由快照只在进页时取一次：它是进程级计数器，没必要跟着每一次重组重读。
    val routeRows = remember { routeSummary() }

    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.stats_page_title),
        navigationIcon = { BackButton() },
    ) {
        item(key = "overview") {
            if (stats.isLoading) StatsSkeleton() else OverviewCard(stats = stats)
        }
        item(key = "heatmap") {
            HeatmapCard(conversationsPerDay = stats.conversationsPerDay)
        }
        item(key = "usage") {
            UsageCard(
                usage = usage,
                range = usageRange,
                onSelectRange = { vm.selectRange(it) },
            )
        }
        if (routeRows.isNotEmpty()) {
            item(key = "routes") {
                RouteCard(rows = routeRows)
            }
        }
    }
}

/** 分组卡片里的一行主数字：上面小字标签，下面 Mochi 计数器。 */
@Composable
private fun HeroMetric(value: Long, label: String, modifier: Modifier = Modifier) {
    val cs = MochiTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MochiTheme.textStyles.subhead,
            color = cs.onSurfaceVariant,
            maxLines = 1,
        )
        MochiCounterText(
            value = value,
            style = MochiTheme.textStyles.largeTitle,
            color = cs.onSurface,
            format = { formatCount(it) },
        )
    }
}

/** 概览里的次要指标：小图标 + 数字 + 标签。 */
@Composable
private fun SmallMetric(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = cs.primary,
        )
        Text(
            text = value,
            style = MochiTheme.textStyles.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = cs.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MochiTheme.textStyles.footnote,
            color = cs.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/* -------------------------------------------------------------------------------------------- */
/* 1. 概览                                                                                       */
/* -------------------------------------------------------------------------------------------- */

@Composable
private fun OverviewCard(stats: AppStats) {
    val cs = MochiTheme.colorScheme
    val divider = cs.outlineVariant.copy(alpha = 0.5f)

    MochiListSection(header = "概览") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeroMetric(
                    value = stats.totalConversations.toLong(),
                    label = stringResource(R.string.stats_page_total_conversations),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(56.dp)
                        .background(divider),
                )
                HeroMetric(
                    value = stats.totalMessages.toLong(),
                    label = stringResource(R.string.stats_page_total_messages),
                    modifier = Modifier.weight(1f),
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(divider))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SmallMetric(
                    value = formatTokens(stats.totalPromptTokens),
                    label = stringResource(R.string.stats_page_input_tokens),
                    icon = memory,
                    modifier = Modifier.weight(1f),
                )
                SmallMetric(
                    value = formatTokens(stats.totalCompletionTokens),
                    label = stringResource(R.string.stats_page_output_tokens),
                    icon = memory,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SmallMetric(
                    value = formatTokens(stats.totalCachedTokens),
                    label = stringResource(R.string.stats_page_cached_tokens),
                    icon = bolt,
                    modifier = Modifier.weight(1f),
                )
                SmallMetric(
                    value = formatCount(stats.launchCount.toLong()),
                    label = stringResource(R.string.stats_page_launch_count),
                    icon = rocketLaunch,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------------------------- */
/* 2. 聊天热力                                                                                   */
/* -------------------------------------------------------------------------------------------- */

@Composable
private fun HeatmapCard(conversationsPerDay: Map<LocalDate, Int>) {
    val cs = MochiTheme.colorScheme
    val today = LocalDate.now()
    val activeDays = remember(conversationsPerDay) {
        conversationsPerDay.count { it.value > 0 }
    }
    val streak = remember(conversationsPerDay, today) { currentStreak(conversationsPerDay, today) }

    MochiListSection(
        header = stringResource(R.string.stats_page_heatmap_title),
        footer = if (streak > 0) "连续 " + streak + " 天" else "活跃 " + activeDays + " 天",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChatHeatmap(conversationsPerDay = conversationsPerDay)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_page_heatmap_less),
                    style = MochiTheme.textStyles.footnote,
                    color = cs.onSurfaceVariant,
                )
                listOf(0.18f, 0.38f, 0.62f, 0.80f, 1f).forEach { alpha ->
                    HeatmapCell(alpha = alpha, sizeDp = 10)
                }
                Text(
                    text = stringResource(R.string.stats_page_heatmap_more),
                    style = MochiTheme.textStyles.footnote,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** 从今天往回数，连续有聊天的天数（今天没聊就从昨天起算，别让「今天还没开聊」显示成 0）。 */
private fun currentStreak(perDay: Map<LocalDate, Int>, today: LocalDate): Int {
    var day = if ((perDay[today] ?: 0) > 0) today else today.minusDays(1)
    var streak = 0
    while ((perDay[day] ?: 0) > 0) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

@Composable
private fun ChatHeatmap(conversationsPerDay: Map<LocalDate, Int>) {
    val today = LocalDate.now()
    val startSunday = today
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        .minusWeeks(52)

    val numWeeks = 53
    val activeCounts = conversationsPerDay.values.filter { it > 0 }.sorted()
    val q1 = activeCounts.getOrElse((activeCounts.size * 0.25).toInt()) { 1 }
    val q2 = activeCounts.getOrElse((activeCounts.size * 0.50).toInt()) { 2 }
    val q3 = activeCounts.getOrElse((activeCounts.size * 0.75).toInt()) { 3 }
    val cellSize = 11.dp
    val cellSpacing = 3.dp
    val monthLabelHeight = 15.dp

    val dowLabels = listOf(
        "",
        stringResource(R.string.stats_page_dow_mon),
        "",
        stringResource(R.string.stats_page_dow_wed),
        "",
        stringResource(R.string.stats_page_dow_fri),
        "",
    )

    // 一次滚动带着月份标签一起走，初值拉到最右（最近的日子）
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            modifier = Modifier.width(12.dp),
            verticalArrangement = Arrangement.spacedBy(cellSpacing),
        ) {
            Spacer(Modifier.height(monthLabelHeight + 2.dp))
            dowLabels.forEach { label ->
                Box(
                    modifier = Modifier.size(cellSize),
                    contentAlignment = Alignment.Center,
                ) {
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            style = MochiTheme.textStyles.footnote.copy(
                                fontSize = MochiTheme.textStyles.footnote.fontSize * 0.72,
                            ),
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.horizontalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(cellSpacing),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                for (weekIdx in 0 until numWeeks) {
                    val weekStart = startSunday.plusDays((weekIdx * 7).toLong())
                    val labelDate = (0..6)
                        .map { weekStart.plusDays(it.toLong()) }
                        .firstOrNull { it.dayOfMonth == 1 }
                    Box(
                        modifier = Modifier
                            .width(cellSize)
                            .height(monthLabelHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        if (labelDate != null) {
                            Text(
                                text = if (labelDate.monthValue == 1) {
                                    labelDate.year.toString()
                                } else {
                                    // LocalConfiguration 可观察：语言切换后重组（Locale.getDefault() 不会）
                                    labelDate.month.getDisplayName(
                                        TextStyle.SHORT,
                                        LocalConfiguration.current.locales[0],
                                    )
                                },
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                                style = MochiTheme.textStyles.footnote.copy(
                                    fontSize = MochiTheme.textStyles.footnote.fontSize * 0.72,
                                ),
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                for (weekIdx in 0 until numWeeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
                        for (dow in 0..6) {
                            val date = startSunday.plusDays((weekIdx * 7 + dow).toLong())
                            val isFuture = date.isAfter(today)
                            val count = if (isFuture) 0 else (conversationsPerDay[date] ?: 0)
                            val alpha = when {
                                isFuture -> -1f
                                count == 0 -> 0f
                                count <= q1 -> 0.18f
                                count <= q2 -> 0.38f
                                count <= q3 -> 0.62f
                                else -> 0.88f
                            }
                            HeatmapCell(alpha = alpha, sizeDp = cellSize.value.toInt())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(alpha: Float, sizeDp: Int) {
    val cs = MochiTheme.colorScheme
    val color = when {
        alpha < 0f -> cs.onSurface.copy(alpha = 0.03f)
        alpha == 0f -> cs.onSurface.copy(alpha = 0.07f)
        else -> cs.primary.copy(alpha = alpha)
    }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(MochiRoundedRectangle(3.dp))
            .background(color),
    )
}

/* -------------------------------------------------------------------------------------------- */
/* 3. 用量                                                                                       */
/* -------------------------------------------------------------------------------------------- */

@Composable
private fun UsageCard(
    usage: UsageStats,
    range: UsageRange,
    onSelectRange: (UsageRange) -> Unit,
) {
    val cs = MochiTheme.colorScheme
    val ranges = UsageRange.entries
    val labels = ranges.map { stringResource(it.labelRes()) }

    Column(verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing)) {
        MochiListSection(
            header = stringResource(R.string.stats_page_usage_title),
            footer = stringResource(R.string.stats_page_usage_requests, formatCount(usage.requests)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MochiSegmentedControl(
                    selectedIndex = ranges.indexOf(range).coerceAtLeast(0),
                    onSelect = { index -> ranges.getOrNull(index)?.let(onSelectRange) },
                    items = persistentListOf(*labels.toTypedArray()),
                    modifier = Modifier.fillMaxWidth(),
                )

                when {
                    usage.isLoading -> {
                        UsageSkeleton()
                    }
                    usage.trend.isEmpty() && usage.byModel.isEmpty() -> {
                        MochiEmptyState(
                            title = stringResource(R.string.stats_page_no_usage),
                            message = "在这个时间窗口里发一条消息，曲线和排行就会出现",
                            icon = {
                                Icon(
                                    imageVector = barChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = cs.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            TokenTotal(
                                label = stringResource(R.string.stats_page_input_tokens),
                                value = usage.promptTokens,
                                modifier = Modifier.weight(1f),
                            )
                            TokenTotal(
                                label = stringResource(R.string.stats_page_output_tokens),
                                value = usage.completionTokens,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (usage.trend.isNotEmpty()) {
                            SectionLabel(stringResource(R.string.stats_page_trend_title))
                            TrendAreaChart(points = usage.trend)
                        }
                    }
                }
            }
        }

        // 排行：模型 / 来源各自一张分组卡，行是标准 MochiListRow（名称 + 次数 + token）。
        if (!usage.isLoading && usage.byModel.isNotEmpty()) {
            MochiListSection(header = stringResource(R.string.stats_page_by_model_title)) {
                usage.byModel.take(MAX_RANKING_ROWS).forEach { model ->
                    RankingRow(
                        name = model.modelName ?: model.modelId ?: "-",
                        requests = model.requests,
                        tokens = model.promptTokens + model.completionTokens,
                    )
                }
            }
        }

        if (!usage.isLoading && usage.bySource.isNotEmpty()) {
            MochiListSection(header = stringResource(R.string.stats_page_by_source_title)) {
                usage.bySource.forEach { source ->
                    RankingRow(
                        name = sourceLabel(source),
                        requests = source.requests,
                        tokens = source.promptTokens + source.completionTokens,
                    )
                }
            }
        }
    }
}

private const val MAX_RANKING_ROWS = 8

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = MochiTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun UsageRange.labelRes(): Int = when (this) {
    UsageRange.WEEK -> R.string.stats_page_range_7d
    UsageRange.MONTH -> R.string.stats_page_range_30d
    UsageRange.QUARTER -> R.string.stats_page_range_90d
    UsageRange.ALL -> R.string.stats_page_range_all
}

/** 来源名（usage_record.source 里存的是 UsageSource 的名字）首字母大写，空值兜底成 "-" */
private fun sourceLabel(source: UsageBySource): String =
    source.source.takeIf { it.isNotBlank() }
        ?.lowercase()
        ?.replaceFirstChar { it.uppercase() }
        ?: "-"

@Composable
private fun TokenTotal(label: String, value: Long, modifier: Modifier = Modifier) {
    val cs = MochiTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MochiCounterText(
            value = value,
            style = MochiTheme.textStyles.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = cs.onSurface,
            format = { formatTokens(it) },
        )
        Text(
            text = label,
            style = MochiTheme.textStyles.footnote,
            color = cs.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun TrendAreaChart(points: List<UsageTrendPoint>) {
    if (points.isEmpty()) return
    val cs = MochiTheme.colorScheme
    val values = remember(points) { points.map { it.promptTokens + it.completionTokens } }
    val maxValue = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 720, easing = FastOutSlowInEasing))
    }
    val gridColor = cs.onSurface.copy(alpha = 0.07f)
    val labelStyle = MochiTheme.textStyles.footnote

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            val top = 10.dp.toPx()
            val bottom = size.height - 4.dp.toPx()
            val chartHeight = bottom - top
            repeat(3) { index ->
                val y = top + chartHeight * index / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (values.isEmpty()) return@Canvas

            val n = values.size
            // 左右各留 4dp：最后一个点的圆点画在 x = width 上会被画布切掉一半
            val left = 4.dp.toPx()
            val right = size.width - 4.dp.toPx()
            val span = (right - left).coerceAtLeast(1f)
            val stepX = if (n > 1) span / (n - 1).toFloat() else 0f
            val dots = values.mapIndexed { index, value ->
                val x = if (n > 1) left + index * stepX else size.width / 2f
                Offset(x, bottom - chartHeight * (value.toFloat() / maxValue.toFloat()))
            }
            val line = Path().apply {
                moveTo(dots.first().x, dots.first().y)
                for (i in 1 until dots.size) {
                    val prev = dots[i - 1]
                    val current = dots[i]
                    val midX = (prev.x + current.x) / 2f
                    cubicTo(midX, prev.y, midX, current.y, current.x, current.y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(dots.last().x, bottom)
                lineTo(dots.first().x, bottom)
                close()
            }
            clipRect(right = size.width * reveal.value) {
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(cs.primary.copy(alpha = 0.32f), cs.primary.copy(alpha = 0f)),
                        startY = top,
                        endY = bottom,
                    ),
                )
                drawPath(
                    path = line,
                    color = cs.primary,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                if (n <= 12) {
                    dots.forEach { dot ->
                        drawCircle(cs.primary, radius = 2.5.dp.toPx(), center = dot)
                    }
                } else {
                    drawCircle(cs.primary, radius = 3.5.dp.toPx(), center = dots.last())
                    drawCircle(cs.surface, radius = 1.6.dp.toPx(), center = dots.last())
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = bucketLabel(points.first().bucket),
                style = labelStyle,
                color = cs.onSurfaceVariant,
            )
            Text(
                text = "峰值 " + formatTokens(maxValue),
                style = labelStyle,
                color = cs.onSurfaceVariant,
            )
            Text(
                text = bucketLabel(points.last().bucket),
                style = labelStyle,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** 桶标签：日桶（yyyy-MM-dd）只留「MM-dd」，周桶（yyyy-Www）只留「Www」。 */
private fun bucketLabel(bucket: String): String = when {
    bucket.contains("-W") -> "W" + bucket.substringAfter("-W")
    bucket.length >= 10 -> bucket.takeLast(5)
    else -> bucket
}

/* -------------------------------------------------------------------------------------------- */
/* 排行行                                                                                        */
/* -------------------------------------------------------------------------------------------- */

/** 排行一行：模型/来源名 + 次数，行尾是 token 总量（标准 iOS 列表行样式）。 */
@Composable
private fun RankingRow(
    name: String,
    requests: Long,
    tokens: Long,
) {
    MochiListRow(
        title = name,
        subtitle = stringResource(R.string.stats_page_row_requests, formatCount(requests)),
        value = formatTokens(tokens),
    )
}

/* -------------------------------------------------------------------------------------------- */
/* 4. 任务路由                                                                                   */
/* -------------------------------------------------------------------------------------------- */

private data class RouteSummary(val label: String, val count: Int)

/** TaskRoutes 的 key 是「任务:模型ID」，这里只按任务聚合，把 UUID 从界面上拿掉。 */
private fun routeSummary(): List<RouteSummary> =
    heizige.kk.khatkit.app.core.data.ai.TaskRoutes.snapshot()
        .entries
        .groupBy { it.key.substringBefore(':') }
        .map { (task, entries) -> RouteSummary(taskLabel(task), entries.sumOf { it.value }) }
        .sortedByDescending { it.count }

private fun taskLabel(task: String): String = when (task) {
    "CHAT" -> "对话"
    "MEMORY" -> "记忆"
    "SUMMARY" -> "摘要"
    "TITLE" -> "标题"
    "OCR" -> "OCR"
    "TRANSLATION" -> "翻译"
    "UI_CONTROL" -> "界面操作"
    else -> task
}

@Composable
private fun RouteCard(rows: List<RouteSummary>) {
    MochiListSection(header = "任务路由", footer = "按任务聚合的模型路由次数，进程内计数器。") {
        rows.forEach { row ->
            MochiListRow(
                title = row.label,
                leading = {
                    Icon(
                        imageVector = bolt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MochiTheme.colorScheme.primary,
                    )
                },
                value = row.count.toString(),
            )
        }
    }
}

/* -------------------------------------------------------------------------------------------- */
/* 加载态                                                                                        */
/* -------------------------------------------------------------------------------------------- */

@Composable
private fun StatsSkeleton(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "stats_skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stats_skeleton_alpha",
    )
    val color = MochiTheme.colorScheme.onSurface
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
    ) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (index == 0) 188.dp else 132.dp)
                    .clip(MochiTheme.shapes.extraLarge)
                    .background(color.copy(alpha = 0.05f + 0.04f * pulse)),
            )
        }
    }
}

@Composable
private fun UsageSkeleton() {
    val pulse by rememberInfiniteTransition(label = "usage_skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "usage_skeleton_alpha",
    )
    val color = MochiTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(30.dp)
                .clip(MochiRoundedRectangle(8.dp))
                .background(color.copy(alpha = 0.05f + 0.04f * pulse)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(MochiRoundedRectangle(14.dp))
                .background(color.copy(alpha = 0.05f + 0.04f * pulse)),
        )
    }
}

/* -------------------------------------------------------------------------------------------- */
/* 数字格式                                                                                      */
/* -------------------------------------------------------------------------------------------- */

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

private fun formatTokens(count: Long): String = when {
    count >= 1_000_000_000 -> "%.2fB".format(count / 1_000_000_000.0)
    count >= 1_000_000 -> "%.2fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}
