package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.kedge.components.KedgeMiuixSurface
import heizige.kk.kedge.components.KedgeSegmentedListSlots
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.theme.LocalKedgeStyle

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

/**
 * [InlineCardGroup] 的组内标记。
 *
 * 只回答「我在不在组里」这一个问题，**不带位号**：组内的行一律用内侧小圆角，外圈
 * （首项上边、末项下边）由 [InlineCardGroup] 那层裁切给出，所以组内放什么、放几个、
 * 什么顺序都不影响形状 —— 见 [InlineCardGroup] 的注释（这里不需要登记表）。
 */
object InlineCardGroupMarker

/** [InlineCardGroup] 下发给自己那一组子项的标记；组外是 `null`。 */
val LocalInlineCardGroup = compositionLocalOf<InlineCardGroupMarker?> { null }

/**
 * 一张分段卡片组：**整组自己画底色、自己收角**，组内子项就地渲染。
 *
 * 三件事都在这一层定下来，所以组内放什么都不影响外形：
 *
 * 1. **组底色**（F1）：`clip(indexedShape(0, 1))` + 底色铺在整组底下。组内那些自己不
 *    画底色的子项（Miuix 的 `ArrowPreference`、MD3 的 `KedgeListItem`、
 *    `KedgeFormCard` 里混进来的标题 `Text` 与任意控件）靠这层才有卡片底；自带底色的行
 *    （`KedgeOptionItem`）盖在它上面。配色来源见 [inlineGroupContainerColor] —— 与改造前
 *    `KedgeFormCard` 那张卡逐值相同，就是 t1 统一行渲染时弄丢的那一层。
 * 2. **组外形**：位号 0 / 总数 1，也就是「一整组只有一行」的形状（四角大圆角），由
 *    `clip` 裁出首项上边、末项下边的大圆角，中间子项因此只需内侧小圆角。
 * 3. **组内间距**：[heizige.kk.khatkit.app.core.ui.theme.ListCardStyle.gap]。
 *
 * ⚠️ 这里**没有位号登记表**（t7 那版有）。原先只有 `FormItem` 会领位号，于是
 * `PreferenceArrow` 这类非 FormItem 子项不占位、条件行又固定拿 `index = 0`，圆角就错
 * （`SettingModelPromptPageMiuix.kt:102/:109` 那组）。现在首项/末项的大圆角由整组裁切
 * 决定，与子项是什么、有几个、什么顺序**都无关** —— 比「所有子项都领一个位号」更强。
 *
 * ⚠️ 已知取舍：整组永远是「四角统一」的裁切（20dp 圆弧），于是 Miuix 下「组内只有一个
 * 子项、且那个子项自带底色」时，圆角不再由 Miuix 自己画 squircle（连续曲率），而是
 * 圆弧。要保留 squircle 就必须知道「组内直接子项数」才能区分该走哪条路，而 `Layout` 的
 * 子项数只能在 measure 时读到 —— 代价是两遍布局 + 首帧闪烁，或者自己重写 `ColumnScope`
 * 的排布（丢掉 `align`/`weight` 语义）。相比之下 20dp 下二者的差别只有曲率连续段，
 * 换「任意子项都有正确圆角」划算。
 *
 * 与 [CardGroup] 的分工：`CardGroup` 是**声明式**的（子项先收集后渲染，能把 index/count
 * 直接给出去）；本组件给的是子项必须留在原处的调用点（`KedgeFormCard`）。
 */
@Composable
fun InlineCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cards = heizige.kk.khatkit.app.core.ui.theme.listCardStyle()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cards.indexedShape(0, 1))
            .background(inlineGroupContainerColor()),
        verticalArrangement = Arrangement.spacedBy(cards.gap),
    ) {
        CompositionLocalProvider(LocalInlineCardGroup provides InlineCardGroupMarker) {
            content()
        }
    }
}

/**
 * 组卡片的底色。
 *
 * - MD3Exp：`CustomColors.cardColorsOnSurfaceContainer.containerColor`（= `surfaceBright`），
 *   与改造前 `KedgeFormCard` 的 MD3 分支（`KedgeCard(colors = CustomColors.cardColorsOnSurfaceContainer)`）
 *   完全一致。
 * - Miuix：`KedgeMiuixSurface.cardContainer`（= `MiuixTheme.colorScheme.surfaceContainer`），
 *   与改造前 Miuix 分支那张卡、以及 `KedgeOptionItem` 自己的行卡片同色。
 *
 * 底色画在整组底下、不额外加内边距，所以不存在「卡套卡」：自带底色的行直接盖住它，
 * 没有第二层圆角与内缩；只有不带底色的子项才「透」出这一层。
 */
@Composable
private fun inlineGroupContainerColor(): Color = when (LocalKedgeStyle.current) {
    KedgeStyle.MD3Exp -> CustomColors.cardColorsOnSurfaceContainer.containerColor
    KedgeStyle.Miuix -> KedgeMiuixSurface.cardContainer
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
