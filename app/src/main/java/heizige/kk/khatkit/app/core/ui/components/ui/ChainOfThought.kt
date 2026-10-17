package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeExpandableOptionItem
import heizige.kk.kedge.components.KedgeOptionItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowUp
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.kedge.theme.KedgeTextStyles

/**
 * 以时间线/步骤卡片的形式展示一组思考过程。
 *
 * 适用于承载推理步骤、工具调用步骤，或两者混合的链式内容。组件支持：
 * - 在步骤较多时自动折叠，仅展示最后若干步
 * - 点击顶部控制条展开/收起全部步骤
 * - 通过 [collapsedAdaptiveWidth] 控制折叠态是否保持自适应宽度
 *
 * @param modifier 外层卡片的修饰符
 * @param cardColors 卡片配色
 * @param steps 需要渲染的步骤数据列表
 * @param collapsedVisibleCount 折叠时保留可见的尾部步骤数
 * @param collapsedAdaptiveWidth 是否在折叠态下使用内容自适应宽度
 * @param showTimeline 是否保留左侧 24dp 时间线槽与卡片内边距。
 *   `true`（默认）是「思考链」形态：卡片底 + 时间线 + 缩进；
 *   `false` 用于**纯工具调用块**——去掉时间线槽与内边距，让 `OptionItemStep`
 *   直接铺满消息宽度（配合透明 `cardColors` 可完全去掉背景层）。
 * @param content 每个步骤的具体 UI，由 [ChainOfThoughtScope] 提供步骤构建能力
 */
@Composable
fun <T> ChainOfThought(
    modifier: Modifier = Modifier,
    cardColors: CardColors = CardDefaults.cardColors(
        containerColor = KedgeColors.surfaceContainerHigh,
    ),
    steps: List<T>,
    collapsedVisibleCount: Int = 2,
    collapsedAdaptiveWidth: Boolean = false,
    showTimeline: Boolean = true,
    content: @Composable ChainOfThoughtScope.(T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val canCollapse = steps.size > collapsedVisibleCount
    val shouldFillCollapseControlWidth = expanded || !collapsedAdaptiveWidth

    KedgeCard(
        modifier = modifier,
        colors = cardColors,
        shape = RoundedCornerShape(16.dp),
        // KedgeCard 默认 contentPadding/insideMargin = 16dp，链式形态靠它 + 下面
        // 的 12dp 缩进凑出 28dp 左边距；无时间线形态必须归零，否则 item 铺不满宽度。
        contentPadding = if (showTimeline) PaddingValues(16.dp) else PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(
                    if (showTimeline) {
                        PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    } else {
                        PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    }
                )
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ),
        ) {
            val visibleSteps = if (expanded || !canCollapse) {
                steps
            } else {
                steps.takeLast(collapsedVisibleCount)
            }

            // 显示展开/折叠按钮（统一在顶部）
            if (canCollapse) {
                val collapseIcon: @Composable () -> Unit = {
                    Icon(
                        imageVector = if (expanded) keyboardArrowUp else keyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = KedgeColors.primary,
                    )
                }
                Row(
                    modifier = Modifier
                        .then(
                            if (shouldFillCollapseControlWidth) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier
                            }
                        )
                        .clip(MaterialTheme.shapes.small)
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showTimeline) {
                        // 左侧：图标区域（24.dp，和步骤图标对齐）
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            collapseIcon()
                        }
                    } else {
                        collapseIcon()
                    }

                    // 右侧：文字区域（8.dp 间距后开始，和步骤 label 对齐）
                    Text(
                        modifier = Modifier.padding(start = if (showTimeline) 8.dp else 6.dp),
                        text = if (expanded) {
                            stringResource(R.string.chain_of_thought_collapse)
                        } else {
                            stringResource(
                                R.string.chain_of_thought_show_more_steps,
                                steps.size - collapsedVisibleCount
                            )
                        },
                        style = KedgeTextStyles.body(),
                        color = KedgeColors.primary,
                    )
                }
            }

            val lineColor = KedgeColors.outlineVariant
            // 每个步骤只绘制自己的连线分段并在节点处留空，
            // 避免使用离屏合成 + BlendMode.Clear（离屏层过大时部分设备会退化，节点区域被清成黑块）。
            val cards = listCardStyle()
            Column {
                visibleSteps.fastForEachIndexed { index, step ->
                    val isFirst = index == 0
                    val isLast = index == visibleSteps.lastIndex
                    // 底块行的位号与行距：同一 ThinkingBlock 的选项行（推理 / 工具调用）
                    // 拼成**一块**分段卡 —— 圆角取 ListCardStyle.indexedShape（首行上圆角 /
                    // 末行下圆角为大圆角，中间行小圆角），行间 0dp 贴合，整块的上下各留 4dp
                    // 呼吸（原来每行 4dp、行间 8dp 的缝就是这么来的）。
                    // 单步链 = indexedShape(0, 1) = 四角大圆角 + 上下各 4dp，与改动前逐值相同。
                    // 裸行（ask_user 那类）走 ChainOfThoughtStepContent，不用这两个值。
                    val optionRowShape = cards.indexedShape(index = index, count = visibleSteps.size)
                    val optionRowPadding = when {
                        visibleSteps.size == 1 -> PaddingValues(vertical = 4.dp)
                        isFirst -> PaddingValues(top = 4.dp, bottom = 0.dp)
                        isLast -> PaddingValues(top = 0.dp, bottom = 4.dp)
                        else -> PaddingValues(0.dp)
                    }
                    val scope = remember(isFirst, isLast, lineColor, showTimeline, optionRowShape, optionRowPadding) {
                        ChainOfThoughtScopeImpl(
                            isFirst = isFirst,
                            isLast = isLast,
                            lineColor = lineColor,
                            showTimeline = showTimeline,
                            optionRowShape = optionRowShape,
                            optionRowPadding = optionRowPadding,
                        )
                    }
                    scope.content(step)
                }
            }
        }
    }
}

/**
 * [ChainOfThought] 内部使用的步骤渲染作用域。
 *
 * 通过该作用域可以声明单个步骤的图标、标题、附加信息以及可展开内容，
 * 并复用统一的时间线布局与交互行为。
 */
interface ChainOfThoughtScope {
    /**
     * 声明一个非受控步骤，由组件内部管理展开/折叠状态。
     *
     * @param icon 步骤图标
     * @param label 步骤标题区域
     * @param extra 标题右侧的附加信息
     * @param onClick 自定义点击行为；设置后优先于展开/折叠逻辑
     * @param collapsedAdaptiveWidth 是否在折叠且内容隐藏时使用自适应宽度
     * @param content 步骤展开后显示的内容；为 `null` 时步骤不可展开
     */
    @Composable
    fun ChainOfThoughtStep(
        icon: (@Composable () -> Unit)? = null,
        label: (@Composable () -> Unit),
        extra: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        collapsedAdaptiveWidth: Boolean = false,
        content: (@Composable () -> Unit)? = null,
    )

    /**
     * 声明一个受控步骤，由外部传入展开状态。
     *
     * 适合需要与外部状态联动的场景，例如“推理中预览 / 完成后收起”。
     *
     * @param expanded 当前是否处于展开状态
     * @param onExpandedChange 展开状态变化回调
     * @param icon 步骤图标
     * @param label 步骤标题区域
     * @param extra 标题右侧的附加信息
     * @param onClick 自定义点击行为；设置后优先于展开/折叠逻辑
     * @param collapsedAdaptiveWidth 是否在折叠且内容隐藏时使用自适应宽度
     * @param contentVisible 是否展示内容区域，可与 [expanded] 解耦
     * @param content 步骤内容；为 `null` 时步骤不可展开
     */
    @Composable
    fun ControlledChainOfThoughtStep(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        icon: (@Composable () -> Unit)? = null,
        label: (@Composable () -> Unit),
        extra: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        collapsedAdaptiveWidth: Boolean = false,
        contentVisible: Boolean = expanded,
        content: (@Composable () -> Unit)? = null,
    )

    /**
     * 声明一个**选项行（OptionItem）样式**的步骤：行的皮换成
     * [KedgeOptionItem] 的圆角底块（左图标 + 标题 + 尾部，16dp 内边距、minHeight 56dp），
     * 有内容可展开时再叠 [KedgeExpandableOptionItem] 的「点击整行切换 + 尾部旋转箭头 +
     * 展开内容」。时间线、展开/折叠交互与普通步骤完全一致。
     *
     * **整链一块**：同一个 [ChainOfThought] 里的选项行按位号取
     * `ListCardStyle.indexedShape(位号, 步骤数)` —— 首行只保上圆角、末行只保下圆角、
     * 中间行小圆角，行间不留缝，整条链读起来是一张分段卡；单步链退化成四角大圆角。
     * 圆角数值全部来自 [heizige.kk.khatkit.app.core.ui.theme.ListCardStyle]
     * （`largeCorner` / `smallCorner`），本文件不新造数值。
     *
     * ⚠️ 位号按**整条链**计，不按「连续段」计：`ChainOfThought` 只拿到 `List<T>`，
     * 容器无法通用地问出每一步是选项行还是裸行（唯一混排的宿主是聊天页，`ask_user`
     * 工具走裸行）。裸行自带 8dp 行距、形状也完全不同，链本来就在那里断开，
     * 所以这里不为它再切一段位号。
     */
    @Composable
    fun OptionItemStep(
        icon: (@Composable () -> Unit)? = null,
        label: (@Composable () -> Unit),
        extra: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        content: (@Composable () -> Unit)? = null,
    )

    /** [OptionItemStep] 的受控版本，语义与 [ControlledChainOfThoughtStep] 一致。 */
    @Composable
    fun ControlledOptionItemStep(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        icon: (@Composable () -> Unit)? = null,
        label: (@Composable () -> Unit),
        extra: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        contentVisible: Boolean = expanded,
        content: (@Composable () -> Unit)? = null,
        /**
         * 展开内容是否与标题行合成同一张卡（透传给
         * [KedgeExpandableOptionItem] 的 `wrapContent`）。默认 `false` = 现状：
         * 内容渲染在底块下方。聊天页推理行传 `true`，工具行保持默认。
         */
        wrapContent: Boolean = false,
    )
}

private class ChainOfThoughtScopeImpl(
    private val isFirst: Boolean,
    private val isLast: Boolean,
    private val lineColor: Color,
    /** 是否保留 24dp 时间线槽与连线；纯工具调用块为 false（见 [ChainOfThought.showTimeline]） */
    private val showTimeline: Boolean = true,
    /** 本行在整块里的圆角：`ListCardStyle.indexedShape(位号, 总数)`，见 [ChainOfThought] 的循环 */
    private val optionRowShape: Shape,
    /** 本行的纵向行距：块内 0dp 贴合，块的上下各留 4dp */
    private val optionRowPadding: PaddingValues,
) : ChainOfThoughtScope {
    @Composable
    override fun ChainOfThoughtStep(
        icon: @Composable (() -> Unit)?,
        label: @Composable (() -> Unit),
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        collapsedAdaptiveWidth: Boolean,
        content: @Composable (() -> Unit)?
    ) {
        var expanded by remember { mutableStateOf(false) }
        ChainOfThoughtStepContent(
            icon = icon,
            label = label,
            extra = extra,
            onClick = onClick,
            collapsedAdaptiveWidth = collapsedAdaptiveWidth,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            contentVisible = expanded,
            content = content,
        )
    }

    @Composable
    override fun ControlledChainOfThoughtStep(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        icon: @Composable (() -> Unit)?,
        label: @Composable (() -> Unit),
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        collapsedAdaptiveWidth: Boolean,
        contentVisible: Boolean,
        content: @Composable (() -> Unit)?
    ) {
        ChainOfThoughtStepContent(
            icon = icon,
            label = label,
            extra = extra,
            onClick = onClick,
            collapsedAdaptiveWidth = collapsedAdaptiveWidth,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            contentVisible = contentVisible,
            content = content,
        )
    }

    @Composable
    override fun OptionItemStep(
        icon: @Composable (() -> Unit)?,
        label: @Composable () -> Unit,
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        content: @Composable (() -> Unit)?
    ) {
        var expanded by remember { mutableStateOf(false) }
        OptionItemStepContent(
            icon = icon,
            label = label,
            extra = extra,
            onClick = onClick,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            contentVisible = expanded,
            content = content,
            optionRowShape = optionRowShape,
            optionRowPadding = optionRowPadding,
            wrapContent = false,
        )
    }

    @Composable
    override fun ControlledOptionItemStep(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        icon: @Composable (() -> Unit)?,
        label: @Composable () -> Unit,
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        contentVisible: Boolean,
        content: @Composable (() -> Unit)?,
        wrapContent: Boolean,
    ) {
        OptionItemStepContent(
            icon = icon,
            label = label,
            extra = extra,
            onClick = onClick,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            contentVisible = contentVisible,
            content = content,
            optionRowShape = optionRowShape,
            optionRowPadding = optionRowPadding,
            wrapContent = wrapContent,
        )
    }

    /**
     * 时间线的一段竖线：节点（20dp）上下各留 10dp 空隙，其余在 x = 12dp 处画 1dp 线。
     *
     * 只给**普通步骤**（槽里有圆点）用；选项行走 [optionItemTimelineLine]，但两条路径的
     * 竖线位置与粗细必须一致（x = 12dp、1dp），否则同一张卡片里的线会错开。
     */
    private fun Modifier.timelineLine(): Modifier = drawBehind {
        val x = 12.dp.toPx()
        val centerY = size.height / 2
        val gap = 10.dp.toPx()
        val strokeWidth = 1.dp.toPx()
        if (!isFirst) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, centerY - gap),
                strokeWidth = strokeWidth,
            )
        }
        if (!isLast) {
            drawLine(
                color = lineColor,
                start = Offset(x, centerY + gap),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth,
            )
        }
    }

    /**
     * 选项行专用的时间线：与 [timelineLine] 同一位置（x = 12dp）、同一粗细，但**不留断口**。
     *
     * [timelineLine] 会在节点高度的一半处留 20dp 给圆点；选项行的槽里**没有**圆点
     * （图标搬进了底块），而且展开内容是这一块的一部分，竖线得跟着长高 —— 按 [timelineLine]
     * 那种「取整块中心」的画法，展开后断口会跟着中心跑到展开内容里去。所以这里直接画满
     * 整个步骤高度，只有上下都没有邻居的单步不画。
     *
     * 首/末块各让开外层的 4dp 竖直内边距，免得竖线从卡片内边距里探出来。
     */
    private fun Modifier.optionItemTimelineLine(): Modifier = drawBehind {
        if (isFirst && isLast) return@drawBehind
        val inset = 4.dp.toPx()
        val x = 12.dp.toPx()
        drawLine(
            color = lineColor,
            start = Offset(x, if (isFirst) inset else 0f),
            end = Offset(x, if (isLast) size.height - inset else size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }

    /**
     * 选项行（[KedgeExpandableOptionItem]）版步骤的渲染。
     *
     * 与 [ChainOfThoughtStepContent] 的两点差异，都是「块」形态逼出来的：
     * 1. 左侧仍留 24dp 的**空**时间线槽（图标搬进底块的 leadingContent），
     *    竖线因此不会被底块盖住 —— 工具调用仍与上下步骤连在同一条链上；
     * 2. 底块恒 `fillMaxWidth`：OptionItem 是整行块，不参与折叠态的自适应宽度
     *    （[ChainOfThought] 的 `shouldFillCollapseControlWidth` 只看控制条）。
     *
     * 展开内容由 [KedgeExpandableOptionItem] 在底块下方渲染，缩进与时间线继续由本文件
     * 负责：内容起点与底块左边缘对齐（32dp 那截已经由外面的空槽 + 8dp 间距占掉了），
     * 竖线则靠上面那条连续画法一路盖过内容区。行距与圆角由 [ChainOfThought] 按位号发下来
     * （[optionRowPadding] / [optionRowShape]），[wrapContent] 时内容改成落进同一张卡。
     */
    @Composable
    private fun OptionItemStepContent(
        icon: @Composable (() -> Unit)?,
        label: @Composable () -> Unit,
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        contentVisible: Boolean,
        content: @Composable (() -> Unit)?,
        /** 本行在整块里的圆角，见 [ChainOfThought] 的 `cards.indexedShape(...)` */
        optionRowShape: Shape,
        /** 本行的纵向行距：块内 0dp 贴合，块的上下各留 4dp */
        optionRowPadding: PaddingValues,
        /** 展开内容是否与标题行合成同一张卡；只有推理行开 */
        wrapContent: Boolean,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (showTimeline) Modifier.optionItemTimelineLine() else Modifier)
                .padding(optionRowPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTimeline) {
                // 空槽：只为让上面那条竖线有地方画（槽宽与普通步骤的图标槽一致）。
                Box(modifier = Modifier.width(24.dp))
            }

            if (content == null) {
                // 没有展开内容 = 没有可展开性，尾部也就不该有箭头（箭头由组件按内容决定）。
                KedgeOptionItem(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    shape = optionRowShape,
                    leadingContent = icon,
                    titleContent = label,
                    trailingContent = extra,
                )
            } else {
                KedgeExpandableOptionItem(
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    shape = optionRowShape,
                    // 推理行开「同卡」：展开内容落在这块之内，而不是块外的第二张卡。
                    // 工具行沿用默认（false），观感一字不变。
                    wrapContent = wrapContent,
                    leadingContent = icon,
                    titleContent = label,
                    trailingContent = extra,
                    contentVisible = contentVisible,
                    // 内容不套内层底色，缩进交给这里：start = 0（对齐底块左边缘，
                    // 时间线的 32dp 由外面的空槽 + 间距占掉），上下补呼吸空间。
                    contentPadding = PaddingValues(start = 0.dp, top = 4.dp, bottom = 8.dp),
                    expandedContent = content,
                )
            }
        }
    }

    @Composable
    private fun ChainOfThoughtStepContent(
        icon: @Composable (() -> Unit)?,
        label: @Composable (() -> Unit),
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        collapsedAdaptiveWidth: Boolean,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        contentVisible: Boolean,
        content: @Composable (() -> Unit)?
    ) {
        val hasContent = content != null
        val shouldFillMaxWidth = !collapsedAdaptiveWidth || contentVisible

        Column(
            modifier = Modifier.then(
                if (shouldFillMaxWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                }
            ),
        ) {
            // Label 行：Icon + Label + Extra + 指示器
            Row(
                modifier = Modifier
                    .then(if (showTimeline) Modifier.timelineLine() else Modifier)
                    .then(
                        if (shouldFillMaxWidth) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (onClick != null) {
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onClick() }
                        } else if (hasContent) {
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onExpandedChange(!expanded) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 无时间线时不留 24dp 空槽，图标本身占位（图标仍要显示，见 ask_user 步骤）
                Box(
                    modifier = if (showTimeline) Modifier.width(24.dp) else Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier.size(14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                icon()
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(KedgeColors.onSurfaceVariant)
                            )
                        }
                    }
                }

                // Label
                Box(
                    modifier = Modifier.then(
                        if (shouldFillMaxWidth) {
                            Modifier.weight(1f)
                        } else {
                            Modifier
                        }
                    )
                ) {
                    label()
                }

                // Extra
                if (extra != null) {
                    extra()
                }

                // 指示器：有展开内容时显示展开/折叠箭头（onClick 不再额外画向右箭头）
                if (hasContent) {
                    Icon(
                        imageVector = if (expanded) keyboardArrowUp else keyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = KedgeColors.onSurfaceVariant,
                    )
                }
            }

            // 展开内容（缩进对齐 label）
            if (contentVisible && hasContent) {
                Box(
                    modifier = Modifier
                        .then(
                            if (shouldFillMaxWidth) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier
                            }
                        )
                        .drawBehind {
                            if (showTimeline && !isLast) {
                                val x = 12.dp.toPx()
                                drawLine(
                                    color = lineColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            }
                        }
                        .padding(
                            start = if (showTimeline) 32.dp else 28.dp,
                            top = 4.dp,
                            bottom = 8.dp,
                        )
                ) {
                    content()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChainOfThoughtPreview() {
    // 定义步骤数据类
    data class StepData(
        val label: String,
        val icon: ImageVector?,
        val status: String?,
        val hasContent: Boolean = false,
        val hasOnClick: Boolean = false,
        val controlled: Boolean = false,
    )

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Chain of thought")
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding),
            ) {
                // 受控状态示例
                var controlledExpanded by remember { mutableStateOf(false) }

                ChainOfThought(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    steps = listOf(
                        StepData("Searching the web", search, "3 results", hasContent = true),
                        StepData("Reading documents", autoAwesome, "Completed", hasOnClick = true),
                        StepData(
                            "Analyzing results (controlled)",
                            autoAwesome,
                            "In progress",
                            hasContent = true,
                            controlled = true
                        ),
                        StepData("Step without icon", null, null),
                        StepData("Final step", autoAwesome, "Done"),
                    ),
                    collapsedVisibleCount = 2,
                ) { step ->
                    val iconComposable: (@Composable () -> Unit)? = step.icon?.let {
                        {
                            Icon(
                                imageVector = it,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                tint = KedgeColors.primary,
                            )
                        }
                    }
                    val labelComposable: @Composable () -> Unit = {
                        Text(step.label, style = KedgeTextStyles.body())
                    }
                    val extraComposable: (@Composable () -> Unit)? = step.status?.let {
                        {
                            Text(
                                it,
                                style = KedgeTextStyles.footnoteSmall(),
                                color = KedgeColors.onSurfaceVariant
                            )
                        }
                    }
                    val onClickHandler: (() -> Unit)? = if (step.hasOnClick) {
                        { /* Open bottom sheet */ }
                    } else null
                    val contentComposable: (@Composable () -> Unit)? = if (step.hasContent) {
                        {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (step.label.contains("Search")) {
                                    listOf(
                                        "example.com - Example Domain",
                                        "docs.example.com - Documentation",
                                        "blog.example.com - Blog Post"
                                    ).forEach { result ->
                                        Text(
                                            text = "• $result",
                                            style = KedgeTextStyles.body(),
                                            color = KedgeColors.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "This is expandable content showing detailed analysis. " +
                                            "It can contain multiple lines of text, code snippets, " +
                                            "or any other composable content.",
                                        style = KedgeTextStyles.body(),
                                    )
                                }
                            }
                        }
                    } else null

                    if (step.controlled) {
                        // 受控版本
                        ControlledChainOfThoughtStep(
                            expanded = controlledExpanded,
                            onExpandedChange = { controlledExpanded = it },
                            icon = iconComposable,
                            label = labelComposable,
                            extra = extraComposable,
                            onClick = onClickHandler,
                            content = contentComposable,
                        )
                    } else {
                        // 非受控版本
                        ChainOfThoughtStep(
                            icon = iconComposable,
                            label = labelComposable,
                            extra = extraComposable,
                            onClick = onClickHandler,
                            content = contentComposable,
                        )
                    }
                }
            }
        }
    }
}
