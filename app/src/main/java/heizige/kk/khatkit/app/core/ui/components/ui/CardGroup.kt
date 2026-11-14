package heizige.kk.khatkit.app.core.ui.components.ui

import heizige.kk.khatkit.uikit.mochi.KkCard
import heizige.kk.khatkit.uikit.mochi.KkAnimatedRow
import heizige.kk.khatkit.uikit.mochi.KkChevron
import heizige.kk.khatkit.uikit.mochi.KkGroupedColumn
import heizige.kk.khatkit.uikit.mochi.kkAnimateContentSize
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSectionFooter
import ui.feice.mochi.theme.MochiTheme

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import heizige.kk.khatkit.app.core.ui.theme.CustomColors

private data class CardGroupItem(
    val onClick: (() -> Unit)?,
    val anchor: String?,
    val modifier: Modifier,
    val overlineContent: (@Composable () -> Unit)?,
    val headlineContent: @Composable () -> Unit,
    val supportingContent: (@Composable () -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val visible: Boolean,
    val chevron: Boolean,
)

@DslMarker
private annotation class CardGroupDsl

@CardGroupDsl
interface CardGroupScope {
    /**
     * @param visible 按条件显隐的行用它，不要写 `if (x) item(...)`：行一直占着位置，
     *   显隐时走 [KkAnimatedRow] 的展开/收起动画；`if` 的写法只能一下蹦出来。
     *   注意隐藏时内容还会在退场动画里画一会儿，所以只适合内容本身总能渲染的行。
     * @param chevron 点了进二级页面的行打开，行尾画统一的 [KkChevron]；
     *   弹窗/选择器/开关/外链行不开。
     * @param anchor 设置搜索的定位锚点（见 [LocalSettingFocusKey]）：这一行被搜索结果命中时，
     *   跳到本页会描边并滚到它。只有「最小选项」行才需要给，纯展示/操作行不给（不给就不会被搜到）。
     */
    fun item(
        onClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        overlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        visible: Boolean = true,
        chevron: Boolean = false,
        anchor: String? = null,
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
        visible: Boolean,
        chevron: Boolean,
        anchor: String?,
        headlineContent: @Composable () -> Unit,
    ) {
        items.add(
            CardGroupItem(
                onClick = onClick,
                anchor = anchor,
                modifier = modifier,
                overlineContent = overlineContent,
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                visible = visible,
                chevron = chevron,
            )
        )
    }
}

@Composable
private fun CardGroupListItem(
    item: CardGroupItem,
    count: Int,
    index: Int,
    topGap: Dp = 0.dp,
) {
    val cards = heizige.kk.khatkit.app.core.ui.theme.listCardStyle()
    val shape = cards.indexedShape(index, count)
    val focusKey = LocalSettingFocusKey.current
    val focused = item.anchor != null && item.anchor == focusKey
    val focusColor by animateColorAsState(
        targetValue = if (focused) MochiTheme.colorScheme.primary else Color.Transparent,
        label = "settingFocusBorder",
    )
    // onClick 原样传下去（可空）：MochiOptionItem 会按 null 关掉整行的点击与
    // 按压回弹。以前这里兜成空 lambda，纯信息行按下也会缩一下，看着像能点。
    heizige.kk.khatkit.uikit.mochi.MochiOptionItem(
        // topGap：分卡排法下的项间距，放在行里而不是外层 spacedBy，跟着显隐动画一起收放
        modifier = Modifier
            .padding(top = topGap)
            .then(item.modifier)
            .fillMaxWidth()
            .kkSettingFocusHighlight(focused = focused, color = focusColor, shape = shape),
        onClick = item.onClick,
        shape = shape,
        leadingContent = item.leadingContent,
        overlineContent = item.overlineContent,
        titleContent = item.headlineContent,
        supportingContent = item.supportingContent,
        trailingContent = item.trailingContent,
        chevron = item.chevron,
    )
}

@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    footer: String? = null,
    content: CardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl()
    scope.content()
    val cards = heizige.kk.khatkit.app.core.ui.theme.listCardStyle()

    Column(modifier = modifier) {
        // Mochi 分组小标题（MochiListSectionHeader）：和行文字一样大、半粗、次要色，
        // 左边与行内文字对齐（16dp），下面留 MochiListDefaults.HeaderGap。
        title?.let { t ->
            CompositionLocalProvider(LocalContentColor provides MochiListDefaults.colors().secondary) {
                ProvideTextStyle(MochiTheme.textStyles.navTitle) {
                    Box(
                        modifier = Modifier.padding(
                            start = MochiListDefaults.TextRowPadding,
                            end = MochiListDefaults.TextRowPadding,
                            bottom = MochiListDefaults.HeaderGap,
                        ),
                    ) {
                        t()
                    }
                }
            }
        }
        if (cards.gap > 0.dp) {
            // 用户在「列表卡片样式」里调出了项间距：退回每项一张卡片的旧排法。
            // 位号只数可见项，首末项的大圆角才落在真正的首末行上；隐藏中的行借下一个可见位号。
            val shown = scope.items.count { it.visible }.coerceAtLeast(1)
            var position = 0
            Column {
                scope.items.forEach { entry ->
                    val index = if (entry.visible) position++ else position
                    KkAnimatedRow(visible = entry.visible) {
                        CardGroupListItem(
                            item = entry,
                            count = shown,
                            index = index.coerceAtMost(shown - 1),
                            topGap = if (index > 0) cards.gap else 0.dp,
                        )
                    }
                }
            }
        } else {
            KkGroupedColumn(
                shape = cards.indexedShape(0, 1),
                containerColor = MochiListDefaults.colors().container,
                // 行自己用 KkAnimatedRow 收放高度；卡片再套一层尺寸动画会互相追，变两套动画。
                animateHeight = false,
            ) {
                scope.items.forEach { entry ->
                    KkAnimatedRow(visible = entry.visible) {
                        CardGroupListItem(item = entry, count = 1, index = 0)
                    }
                }
            }
        }
        // 分组说明：走 Mochi 列表的同一套（footnote、次要色、与行文字同缩进、上面留 8dp）
        footer?.let { text ->
            MochiListSectionFooter(text, color = MochiListDefaults.colors().secondary)
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
 *    画底色的子项（Miuix 的 `ArrowPreference`、MD3 的 `MochiListItem`、
 *    `MochiFormCard` 里混进来的标题 `Text` 与任意控件）靠这层才有卡片底；自带底色的行
 *    （`MochiOptionItem`）盖在它上面。配色来源见 [inlineGroupContainerColor] —— 与改造前
 *    `MochiFormCard` 那张卡逐值相同，就是 t1 统一行渲染时弄丢的那一层。
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
 * 直接给出去）；本组件给的是子项必须留在原处的调用点（`MochiFormCard`）。
 */
@Composable
fun InlineCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cards = heizige.kk.khatkit.app.core.ui.theme.listCardStyle()
    CompositionLocalProvider(LocalInlineCardGroup provides InlineCardGroupMarker) {
        if (cards.gap > 0.dp) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    // 分卡排法也要和整卡一样平滑变高（Mochi 同款尺寸弹簧），见 KkGroupedColumn 的同名参数。
                    .kkAnimateContentSize()
                    .clip(cards.indexedShape(0, 1))
                    .background(inlineGroupContainerColor()),
                verticalArrangement = Arrangement.spacedBy(cards.gap),
                content = content,
            )
        } else {
            // Mochi 分组：一张卡，行间 0.5dp 分隔线（从行文字起点开始），按下行高亮时分隔线淡出
            KkGroupedColumn(
                modifier = modifier,
                shape = cards.indexedShape(0, 1),
                containerColor = inlineGroupContainerColor(),
                content = content,
            )
        }
    }
}

/**
 * 组卡片的底色。
 *
 * - MD3Exp：`CustomColors.cardColorsOnSurfaceContainer.containerColor`（= `surfaceContainerHigh`，
 *   见 `themecompat/Color.kt` 里 CustomColors 的说明：刻意不用 `surfaceBright` —— 那个角色浅色下
 *   与 surface 同色会让分组整片消失、深色下又比 surface 亮太多），
 *   与改造前 `MochiFormCard` 的 MD3 分支（`KkCard(colors = CustomColors.cardColorsOnSurfaceContainer)`）同源。
 * - Miuix：`MaterialTheme.colorScheme.surfaceContainer`（= `MochiTheme.colorScheme.surfaceContainer`），
 *   与改造前 Miuix 分支那张卡、以及 `MochiOptionItem` 自己的行卡片同色。
 *
 * 底色画在整组底下、不额外加内边距，所以不存在「卡套卡」：自带底色的行直接盖住它，
 * 没有第二层圆角与内缩；只有不带底色的子项才「透」出这一层。
 */
@Composable
private fun inlineGroupContainerColor(): Color = MochiListDefaults.colors().container

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
                    chevron = true,
                    headlineContent = { Text("第三项") },
                )
            }
        }
    }
}