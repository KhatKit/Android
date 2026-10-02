package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import heizige.kk.khromia.components.pressBounce
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.kedge.components.KedgeSegmentedListSlots
import heizige.kk.kedge.theme.KedgeTextStyles

private data class CardGroupItem(
    val onClick: (() -> Unit)?,
    val modifier: Modifier,
    val overlineContent: (@Composable () -> Unit)?,
    val headlineContent: @Composable () -> Unit,
    val supportingContent: (@Composable () -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
)

@DslMarker
private annotation class CardGroupDsl

@CardGroupDsl
interface CardGroupScope {
    fun item(
        onClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        overlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        headlineContent: @Composable () -> Unit,
    )
}

private class CardGroupScopeImpl : CardGroupScope {
    val items = mutableListOf<CardGroupItem>()

    override fun item(
        onClick: (() -> Unit)?,
        modifier: Modifier,
        overlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        headlineContent: @Composable () -> Unit,
    ) {
        items.add(
            CardGroupItem(
                onClick = onClick,
                modifier = modifier,
                overlineContent = overlineContent,
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
            )
        )
    }
}

@Composable
private fun CardGroupListItem(
    item: CardGroupItem,
    count: Int,
    index: Int,
) {
    val cards = heizige.kk.khatkit.app.core.ui.theme.listCardStyle()
    // onClick 原样传下去（可空）：KedgeOptionItem 会按 null 关掉整行的点击与
    // 按压回弹。以前这里兜成空 lambda，纯信息行按下也会缩一下，看着像能点。
    heizige.kk.kedge.components.KedgeOptionItem(
        modifier = item.modifier.fillMaxWidth(),
        onClick = item.onClick,
        shape = cards.indexedShape(index, count),
        leadingContent = item.leadingContent,
        overlineContent = item.overlineContent,
        titleContent = item.headlineContent,
        supportingContent = item.supportingContent,
        trailingContent = item.trailingContent,
    )
}

@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    content: CardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl()
    scope.content()
    val cards = heizige.kk.khatkit.app.core.ui.theme.listCardStyle()

    KedgeSegmentedListSlots(
        modifier = modifier,
        itemGap = cards.gap,
        title = title?.let { t ->
            {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary.copy(alpha = 0.54f)) {
                    ProvideTextStyle(
                        KedgeTextStyles.title().copy(
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            lineHeight = 20.sp,
                        )
                    ) {
                        Box(modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                            t()
                        }
                    }
                }
            }
        },
    ) {
        scope.items.forEach { entry ->
            item { itemIndex, itemCount ->
                CardGroupListItem(item = entry, count = itemCount, index = itemIndex)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CardGroupPreview() {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("Card Group")
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.pageContainerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            CardGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = { Text("About") },
            ) {
                item(
                    headlineContent = { Text("第一项") },
                )
                item(
                    headlineContent = { Text("第二项") },
                    supportingContent = { Text("支持文本") },
                )
                item(
                    onClick = {},
                    headlineContent = { Text("第三项") },
                    trailingContent = { Text("→") },
                )
            }
        }
    }
}
