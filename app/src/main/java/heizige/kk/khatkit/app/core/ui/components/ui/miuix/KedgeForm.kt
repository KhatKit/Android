package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「表单分组 / 表单项 / 分隔线」的双风格版本。
 *
 * ⚠️ 这个文件**不再自绘行**。设置表单行（`FormItem` / [KedgeFormRow] /
 * [KedgeSwitchFormRow] 这一系）的渲染只有一条路径：
 * [heizige.kk.khatkit.app.core.ui.components.ui.FormItem] → `KedgeOptionItem`
 * （后者内部按 `LocalKedgeStyle` 分流 MD3 的 Khromia `OptionItem` / Miuix 的
 * `KedgeCard` 行）。以前这里有第二份自绘行（MD3 分支转 FormItem、Miuix 分支又
 * `KedgeCard` + `Column` 自己排一遍），改一处漏一处；现在 [KedgeFormRow] 与
 * [KedgeSwitchFormRow] 只是**签名转接层**，不做任何布局与配色决策。
 *
 * ⚠️ 上面那句只覆盖这一系，不是全仓结论：`miuix/PreferenceItems.kt:194-235` 与
 * `ProviderModelList.kt:264-277` 至今仍在自绘行 / 自绘分组，本次没有动它们。
 *
 * 调用点保持原样：原来那张「大卡片 + 分隔线」的容器换成 [KedgeFormCard]、
 * `HorizontalDivider()` 换成 [KedgeFormDivider]、`FormItem(…)` 换成 [KedgeFormRow]
 * 即可，签名与语义都不变。
 */

/**
 * 表单分组：**一整组分段卡片**。
 *
 * 两种风格下都是「组内子项各占一段、整组看是一张卡」：组内间距走
 * [heizige.kk.khatkit.app.core.ui.theme.ListCardStyle.gap]（Miuix 0dp 贴成一张、
 * MD3 2dp），整组的底色与外形都由这一层
 * （[heizige.kk.khatkit.app.core.ui.components.ui.InlineCardGroup]）出：底色按
 * `indexedShape(0, 1)` 收角后铺在整组**底下**、不额外加内边距，所以不是「卡套卡」——
 * 自带底色的行（`KedgeOptionItem`）直接盖住它，没有第二层圆角与内缩；反向地，组里那些
 * 不带底色的子项（Miuix 的 `ArrowPreference`、MD3 的 `KedgeListItem`、本组混进来的标题
 * `Text`）就是靠这一层才有卡片底 —— t1 统一行渲染时弄丢的正是它。
 *
 * 组内子项是**就地渲染**的，而且**不限于行组件**：调用点里常常混着 `BackgroundPicker`、
 * `PreferenceArrow`、`Column { … }` 这类任意 composable。首项上边 / 末项下边的大圆角由
 * 整组裁切决定，与子项是什么、有几个、什么顺序都无关，所以这里**不要求**「组内只允许
 * `KedgeOptionItem` 行」。
 *
 * ⚠️ 内边距不在这一层：组内是行组件时，内边距由行自己带（见
 * [heizige.kk.khatkit.app.core.ui.components.ui.FormItem] 的 `modifier` 注释）；组内是
 * 「标题 + 任意控件」时，由调用点自己补（见 `ProviderModelList.ModelFormGroup`）。
 */
@Composable
fun KedgeFormCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // 组顶间距（**Miuix 专属**）：基线 HEAD `KedgeForm.kt:53` 只给 Miuix 那一支的组卡片
    // 写了 `.padding(top = MiuixFormMetrics.ItemSpacing)`（= `:159` 的 10dp），t1 把
    // 表单统一进单一渲染路径时，它随着那张 `KedgeCard` 一起丢了。这里把**同一个常量**加回
    // 组容器：整组（含底色、含圆角）整体下移 10dp，不新增第二张卡、不新增第二套行渲染。
    // MD3 那一支基线是 `KedgeCard(modifier = modifier.fillMaxWidth())`，没有这一层间距，
    // 所以 `when` 的 MD3 分支恒返回 `this` —— 数值逐值不变（0dp 额外间距）。
    val groupModifier = when (LocalKedgeStyle.current) {
        KedgeStyle.MD3Exp -> modifier
        KedgeStyle.Miuix -> modifier.padding(top = MiuixFormMetrics.ItemSpacing)
    }
    heizige.kk.khatkit.app.core.ui.components.ui.InlineCardGroup(
        modifier = groupModifier,
        content = content,
    )
}

/**
 * 开关行：整行点击等价于翻转开关。
 *
 * 之前所有这类行都只把 `KedgeSwitch` 塞进 [KedgeFormRow] 的 `tail`，行本身不可点，
 * 于是「点卡片不动、只能点右侧那个小开关」。这里统一收口，别再逐处接 onClick。
 */
@Composable
fun KedgeSwitchFormRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit = {},
    description: (@Composable (() -> Unit))? = null,
) {
    KedgeFormRow(
        modifier = modifier,
        label = label,
        description = description,
        tail = {
            KedgeSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

/**
 * 表单里的一行。签名与 [heizige.kk.khatkit.app.core.ui.components.ui.FormItem] 一致，
 * 可以直接替换——实际上现在就是直接转调它，两种风格下的差异（MD3 圆角行 / Miuix
 * 圆角卡片行、字阶、内边距）全部由 `KedgeOptionItem` 决定，本文件不再参与。
 *
 * 槽位对照：`label` → 标题，`description` + `content` → supporting（描述在上、输入框在下），
 * `tail` → trailing。行内输入框从「框内 label」改成「标题独立成行 + 框在下方」，
 * 所以标题不会再画两次。
 *
 * ⚠️ [modifier] 沿用调用点一直以来的语义：**行内容的内边距**（调用点写死的
 * `padding(8.dp)`），不是卡片外框的尺寸约束 —— 具体见
 * [heizige.kk.khatkit.app.core.ui.components.ui.FormItem] 的注释。卡片外框永远满宽，
 * 组内圆角由 [KedgeFormCard] 那一组的位号决定。
 */
@Composable
fun KedgeFormRow(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit = {},
    description: (@Composable (() -> Unit))? = null,
    tail: @Composable () -> Unit = {},
    /**
     * 整行点击。开关行传 `onClick = { onCheckedChange(!checked) }` —— 否则整行
     * 点不动，用户只能去点右侧那个小开关。传进来的开关自己消费点击，不会冒泡回来，
     * 所以不会翻两次。
     */
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    heizige.kk.khatkit.app.core.ui.components.ui.FormItem(
        modifier = modifier,
        label = label,
        description = description,
        tail = tail,
        onClick = onClick,
        content = content,
    )
}

/**
 * `description` + `content` 合成 supporting 槽：描述在上、行内内容在下。
 *
 * **唯一**定义顺序的地方——[heizige.kk.khatkit.app.core.ui.components.ui.FormItem]
 * 不支持 `ColumnScope` 的 `content`，就是为了让这里成为两种风格共用的排布点，
 * 而不是每个组件各写一遍 `Column`。
 */
@Composable
internal fun KedgeRowSupport(
    description: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        description?.invoke()
        content()
    }
}

/**
 * 表单分隔线。
 *
 * **分段卡片组里不渲染**（[KedgeFormCard] 内）：组的分段形态本身就是分隔，行与行之间
 * 只留 `ListCardStyle.gap` 的间距；再画一条线就正好落在两张卡中间，变成「卡片之间浮
 * 一条分隔线」，比不画差得多。组外（日志页那种分段排版）保持原样：MD3 画线、Miuix 不画。
 */
@Composable
fun KedgeFormDivider(modifier: Modifier = Modifier) {
    if (heizige.kk.khatkit.app.core.ui.components.ui.LocalInlineCardGroup.current != null) return
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) return
    HorizontalDivider(modifier = modifier)
}

/** Miuix 表单行的度量，与设置页（ArrowPreference）保持一致。 */
@Suppress("unused")
object MiuixFormMetrics {
    /** 行之间的间距，与设置页（miuixGroup 的 GroupSpacing）保持一致。 */
    val ItemSpacing = 10.dp

    /** 行卡片的圆角。 */
    val ItemShape = 20.dp

    /** 行卡片内边距。 */
    val ItemPadding = 10.dp

    /** 组内纵向间距，Miuix 下比 MD3 紧。 */
    val GroupSpacing = 10.dp

    /**
     * 组内块的内边距。
     *
     * 早就没人往 `KedgeFormCard` 里写这个常量了：调用点写的是**字面量**
     * `padding(8.dp)`，而且那个 8dp 是「行内容的内边距」的语义（见 [KedgeFormRow]），
     * 由 `FormItem` 套在行内容上，和这里没有关系。保留它只是为了不动老调用点的字面量
     * 引用（本值恒为 0，不要在新代码里用）。
     */
    val InnerPadding = 0.dp
}

/**
 * 行标题：用 Miuix 的 headline1 + Medium，和设置页一致。
 *
 * 只在少数调用点自己排版时使用（[KedgeFormRow] 内部**不用**它——行标题的排版现在
 * 由 `KedgeOptionItem` 负责）。
 */
@Composable
internal fun MiuixRowLabel(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        androidx.compose.material3.LocalTextStyle provides
            MiuixTheme.textStyles.headline1.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            ),
        content = content,
    )
}
