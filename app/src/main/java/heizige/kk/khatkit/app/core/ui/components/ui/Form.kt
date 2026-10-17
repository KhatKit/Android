package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeRowSupport
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle

/**
 * 表单行：左侧标题 + 描述，行内输入框 / 开关放 [tail]。
 *
 * **设置表单行（`FormItem` / `KedgeFormRow` / `KedgeSwitchFormRow` 这一系）唯一的行渲染
 * 路径**——本组件只做槽位映射，布局全部交给 [KedgeOptionItem]（它自己按
 * `LocalKedgeStyle` 分流：MD3 走 Khromia `OptionItem`，Miuix 走 `KedgeCard` 行）。这里不再
 * 手写行布局：以前 Form.kt 与 `miuix/KedgeForm.kt` 各有一份自绘行，同一件事两套实现，
 * 改一处另一处就漏。
 *
 * ⚠️ 别把这句读成全仓：`miuix/PreferenceItems.kt:194-235`（`PreferenceArrow` 等）与
 * `ProviderModelList.kt:264-277` 至今仍在自绘行/自绘分组，本次没动它们。
 *
 * 槽位映射：
 * - [label] → `titleContent`（字阶见 [RowTitleStyle]）
 * - [description] + [content] → `supportingContent`（描述在上，行内内容在下，
 *   顺序由 [KedgeRowSupport] 统一决定；描述的字阶见 [RowSupportingStyle]）
 * - [tail] → `trailingContent`
 *
 * [onClick] 传 `null` 表示不可点的信息行；`KedgeOptionItem` 会同时关掉整行点击与
 * 按压回弹，所以「纯信息行看着像能点」的问题不会再出现。
 *
 * ⚠️ [modifier] 是**行内容的内边距**，不是卡片外框的尺寸约束。调用点长期写
 * 「`KedgeFormRow` + `modifier = padding(8.dp)`」，那个 8dp 的语义一直是
 * 「组卡片里的内容再往里缩一圈」（改前的 MD3 `FormItem` 就是把它套在卡片**内部**的
 * Row 上）。所以它只落在 [label] / [content] / [tail] 这几个槽位上，卡片外框永远是
 * 满宽：原样交给 `KedgeOptionItem` 会把卡片本身一起缩窄，行卡片就比同页
 * [CardGroup] 的卡片窄一圈。
 *
 * 卡片圆角由 [inlineGroupRowShape] 给出：组内一律内侧小圆角，首项上边/末项下边的大圆角
 * 由 [InlineCardGroup] 整组裁切；组外单行按「整组只有一行」算。
 */
@Composable
fun FormItem(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    description: @Composable (() -> Unit)? = null,
    tail: @Composable () -> Unit = {},
    /**
     * 整行点击。开关行必须传（等于翻转开关），否则「点卡片不动、只能点开关」。
     */
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    KedgeOptionItem(
        onClick = onClick,
        // 卡片外框一律满宽（`Modifier` 空修饰符，KedgeOptionItem 自己 fillMaxWidth）；
        // 调用点的 [modifier] 见上面注释，套槽位不套卡片。
        modifier = Modifier,
        shape = inlineGroupRowShape(),
        titleContent = { SlotContent(modifier) { RowTitleStyle { label() } } },
        // description 为 null 时只套 content：空 content 渲染出来是空的，
        // 不必再判一次「调用点给没给内容」。
        supportingContent = if (description != null) {
            {
                SlotContent(modifier) {
                    KedgeRowSupport(
                        // 字阶只包 description，不包 content：调用点塞进来的输入框
                        // 不该继承辅助文字的字号（改造前的 FormItem 也是这个边界）。
                        description = { RowSupportingStyle { description() } },
                        content = content,
                    )
                }
            }
        } else {
            { SlotContent(modifier) { content() } }
        },
        trailingContent = { SlotContent(modifier) { tail() } },
    )
}

/**
 * 行内容的内边距：调用点的 [Modifier] 只作用在槽位内容上（见 [FormItem]），
 * 不落到卡片外框。空槽位套一个空修饰符时它只是个空 `Box`，不产生额外尺寸。
 */
@Composable
private fun SlotContent(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier) {
        content()
    }
}

/**
 * 行标题的字阶（F2）。
 *
 * **MD3 下必须由这里补**：`KedgeOptionItem` 的 MD3 分支转给 Khromia `OptionItem`，而它在
 * `Option.kt:300-302` 直接调 `titleContent()` / `supportingContent()`，不给任何字阶 ——
 * 调用点写的是没带 `style` 的 `Text(...)`，就会漏出 MD3 默认的 `bodyLarge`（标题与描述
 * 同字号字重）。Miuix 分支**不动**：`KedgeOptionItem` 已经用
 * `MiuixTextStyleScope(headline1 + Medium)` 包了标题槽，这里再包一层会改掉 Miuix 的字阶。
 *
 * 值取自 [KedgeTextStyles.title]（MD3 = `titleMedium`），与统一前的 `FormItem` 一致；
 * 调用点显式写了 `style` 的像素不变 —— 组件自带的样式优先于这里提供的。
 */
@Composable
private fun RowTitleStyle(content: @Composable () -> Unit) {
    if (LocalKedgeStyle.current == KedgeStyle.MD3Exp) {
        ProvideTextStyle(value = KedgeTextStyles.title(), content = content)
    } else {
        content()
    }
}

/**
 * 行描述的字阶（F2）：MD3 = `KedgeTextStyles.footnoteSmall()`（`labelSmall`，12sp）叠
 * `0.6` alpha 的前景色，与统一前的 `FormItem` 逐值相同。
 *
 * 与 [RowTitleStyle] 同样只在 MD3 下生效（Miuix 由 `KedgeOptionItem` 给 `body2`）。
 */
@Composable
private fun RowSupportingStyle(content: @Composable () -> Unit) {
    if (LocalKedgeStyle.current == KedgeStyle.MD3Exp) {
        ProvideTextStyle(
            value = KedgeTextStyles.footnoteSmall().copy(
                color = LocalContentColor.current.copy(alpha = 0.6f),
            ),
            content = content,
        )
    } else {
        content()
    }
}

/**
 * 行卡片的圆角。
 *
 * - 组外（单行）：按「整组只有一行」算，也就是四角都是大圆角 —— 与 `KedgeOptionItem`
 *   的默认 20dp 一致。
 * - 组内：一律按「中间项」算（只留内侧小圆角），**不区分首项/末项、不数位号**。
 *
 * 首项上边与末项下边的大圆角不在这里，由 [InlineCardGroup] 整组裁切
 * （`clip(indexedShape(0, 1))`）给出。等价性：MD3 下首项 = 自己的小圆角 + 裁出来的上边大
 * 圆角，末项 = 自己的小圆角 + 裁出来的下边大圆角，中间项 = 小圆角（裁切碰不到中间项），
 * 与逐项 `indexedShape(index, count)` 的结果逐像素相同；Miuix 下 `smallCorner = 0`，
 * 组内就是直角矩形拼一张卡，正好是 KernelSU 的分组观感。
 *
 * 这条路径**不需要知道自己在组里的第几个**，也就不需要位号表：组内放 `PreferenceArrow`、
 * 条件行还是别的 composable 都一样正确（见 [InlineCardGroup]）。
 */
@Composable
private fun inlineGroupRowShape(): Shape {
    val cards = listCardStyle()
    return if (LocalInlineCardGroup.current == null) {
        cards.indexedShape(0, 1)
    } else {
        cards.groupItemShape(isFirst = false, isLast = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun FormItemPreview() {
    FormItem(
        label = { Text("Label") },
        content = { Text("content") },
        description = { Text("Description") },
        tail = { Text("tail") },
        modifier = Modifier.padding(4.dp),
    )
}
