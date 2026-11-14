package heizige.kk.khatkit.app.core.ui.components.explore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.icons.*
import heizige.kk.khatkit.card.CardManifest
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.theme.MochiTheme

/**
 * 简洁的探索市场卡片行
 *
 * 设计理念：
 * - 统一的列表卡片风格
 * - 显示卡片名称、描述、安装状态
 * - 移除复杂的标签和轮播
 */
@Composable
fun SimpleMarketCardRow(
    manifest: CardManifest,
    isInstalled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme

    MochiListRow(
        modifier = modifier,
        title = manifest.name,
        subtitle = manifest.description,
        leading = {
            Icon(
                imageVector = extension,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        value = if (isInstalled) "已安装" else null,
        chevron = true,
        onClick = onClick
    )
}

/**
 * 简洁的工具行
 */
@Composable
fun SimpleToolRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme

    MochiListRow(
        modifier = modifier,
        title = title,
        subtitle = description,
        leading = {
            Icon(
                imageVector = code,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        chevron = true,
        onClick = onClick
    )
}

/**
 * 简洁的 MCP 服务行
 */
@Composable
fun SimpleMcpServerRow(
    name: String,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme

    MochiListRow(
        modifier = modifier,
        title = name,
        subtitle = "MCP 服务",
        leading = {
            Icon(
                imageVector = dns,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        value = status,
        chevron = true,
        onClick = onClick
    )
}
