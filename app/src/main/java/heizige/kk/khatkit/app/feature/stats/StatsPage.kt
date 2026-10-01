package heizige.kk.khatkit.app.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import heizige.kk.khatkit.app.core.ui.icons.barChart
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.chat
import heizige.kk.khatkit.app.core.ui.icons.memory
import heizige.kk.khatkit.app.core.ui.icons.rocketLaunch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun StatsPage(vm: StatsVM = hiltViewModel()) {
    val stats by vm.stats.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.stats_page_title),
        scrollBehavior = scrollBehavior,
    ) { padding ->
        if (stats.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                KedgeProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    HeatmapCard(
                        conversationsPerDay = stats.conversationsPerDay,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                item {
                    StatsGrid(
                        stats = stats,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(conversationsPerDay: Map<LocalDate, Int>, modifier: Modifier = Modifier) {
    KedgeCard(
        modifier = modifier.fillMaxWidth(),
        ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.stats_page_heatmap_title), style = KedgeTextStyles.title())

            ChatHeatmap(conversationsPerDay = conversationsPerDay)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stats_page_heatmap_less),
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { alpha ->
                    HeatmapCell(alpha = alpha, sizeDp = 10)
                }
                Spacer(Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.stats_page_heatmap_more),
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
    val cellSpacing = 2.dp
    // Month label row height
    val monthLabelHeight = 14.dp

    // Day-of-week labels (only Mon/Wed/Fri to save space, Sun=0)
    val dowLabels = listOf(
        "",
        stringResource(R.string.stats_page_dow_mon),
        "",
        stringResource(R.string.stats_page_dow_wed),
        "",
        stringResource(R.string.stats_page_dow_fri),
        ""
    )

    // Shared scroll state so month labels + grid scroll together
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // Fixed left column: spacer for month label row + DOW labels
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
                            style = KedgeTextStyles.footnoteSmall(),
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.7,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Scrollable area: month labels + heatmap grid share one scroll state
        Column(
            modifier = Modifier.horizontalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Month labels row
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
                                    labelDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                },
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                                style = KedgeTextStyles.footnoteSmall(),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.75,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Heatmap grid
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
                                count <= q1 -> 0.25f
                                count <= q2 -> 0.5f
                                count <= q3 -> 0.75f
                                else -> 1f
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
    val color = when {
        alpha < 0f -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) // future
        alpha == 0f -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color)
    )
}

@Composable
private fun StatsGrid(stats: AppStats, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = barChart,
                label = stringResource(R.string.stats_page_total_conversations),
                value = formatCount(stats.totalConversations.toLong()),
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = chat,
                label = stringResource(R.string.stats_page_total_messages),
                value = formatCount(stats.totalMessages.toLong()),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = memory,
                label = stringResource(R.string.stats_page_input_tokens),
                value = formatTokens(stats.totalPromptTokens),
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = memory,
                label = stringResource(R.string.stats_page_output_tokens),
                value = formatTokens(stats.totalCompletionTokens),
            )
        }
        if (stats.totalCachedTokens > 0) {
            StatCard(
                modifier = Modifier.fillMaxWidth(),
                icon = bolt,
                label = stringResource(R.string.stats_page_cached_tokens),
                value = formatTokens(stats.totalCachedTokens),
            )
        }
        StatCard(
            modifier = Modifier.fillMaxWidth(),
            icon = rocketLaunch,
            label = stringResource(R.string.stats_page_launch_count),
            value = formatCount(stats.launchCount.toLong()),
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
) {
    KedgeCard(modifier = modifier, colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = label,
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
