package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.theme.MiuixTheme
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.kedge.theme.KedgeStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.theme.extendColors

enum class TagType {
    DEFAULT,
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

@Composable
fun Tag(
    modifier: Modifier = Modifier,
    type: TagType = TagType.DEFAULT,
    onClick: (() -> Unit)? = null,
    children: @Composable RowScope.() -> Unit
) {
    val background = when (type) {
        TagType.SUCCESS -> MaterialTheme.extendColors.green2
        TagType.ERROR -> MaterialTheme.extendColors.red2
        TagType.WARNING -> MaterialTheme.extendColors.orange2
        TagType.INFO -> MaterialTheme.extendColors.blue2
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val textColor = when (type) {
        TagType.SUCCESS -> MaterialTheme.extendColors.gray8
        TagType.ERROR -> MaterialTheme.extendColors.red8
        TagType.WARNING -> MaterialTheme.extendColors.orange8
        TagType.INFO -> MaterialTheme.extendColors.blue8
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    // Miuix 下这套 MD3 的 labelSmall + tertiaryContainer 观感不对：换成 Miuix 的
    // 表面色层级与文字样式，形状保持胶囊（Miuix 也是胶囊）。
    val isMiuix = LocalKedgeStyle.current == KedgeStyle.Miuix
    val finalBackground = if (isMiuix) MiuixTheme.colorScheme.surfaceContainer else background
    val finalTextColor = if (isMiuix) MiuixTheme.colorScheme.onSurfaceVariantSummary else textColor
    val finalTextStyle = if (isMiuix) {
        MiuixTheme.textStyles.footnote1.copy(color = finalTextColor)
    } else {
        MaterialTheme.typography.labelSmall.copy(color = textColor)
    }
    val hPad = if (isMiuix) 10.dp else 6.dp
    val vPad = if (isMiuix) 4.dp else 1.dp

    ProvideTextStyle(finalTextStyle) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(finalBackground)
                .let {
                    if (onClick != null) {
                        it.clickable { onClick() }
                    } else {
                        it
                    }
                }
                .padding(horizontal = hPad, vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            children()
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun TagPreview() {
    Column(
        modifier = Modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Tag(type = TagType.SUCCESS) {
            Text("测试")
        }
        Tag(type = TagType.ERROR) {
            Text("测试")
        }
        Tag(type = TagType.WARNING) {
            Text("测试")
        }
        Tag(type = TagType.INFO) {
            Text("测试")
        }
        Tag(type = TagType.DEFAULT) {
            Text("测试")
        }
    }
}
