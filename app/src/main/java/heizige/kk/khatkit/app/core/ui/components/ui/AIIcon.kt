package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.hooks.rememberAvatarShape
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import heizige.kk.khatkit.app.core.util.computeAIIconByName
import heizige.kk.kedge.components.KedgeSurface

@Composable
private fun AIIcon(
    path: String,
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    /**
     * 底色。缺省 [Color.Unspecified] = 主题 `secondaryContainer`（中性底，不再按提供商
     * 分品牌色）；调用点显式传色时以调用点为准，行为与改动前一致。
     */
    color: Color = Color.Unspecified,
) {
    val background = color.takeOrElse { MaterialTheme.colorScheme.secondaryContainer }
    // 图形色（单色）：缺省主题的 onSecondaryContainer（底色上的内容色）；显式传色时沿用环境
    // 内容色 —— 与改动前「CSS fill 取 LocalContentColor」的行为一致。
    val contentColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        LocalContentColor.current
    }
    val context = LocalContext.current
    val model = remember(path, context) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/icons/$path")
            .build()
    }
    KedgeSurface(
        modifier = modifier.size(24.dp),
        shape = rememberAvatarShape(loading),
        color = background,
        contentColor = contentColor,
    ) {
        AsyncImage(
            model = model,
            contentDescription = name,
            // 单色由这一层**结构性**保证，而不是靠「资产本来就是单色」：对每个资产
            // （SVG 与 PNG 一视同仁）统一 SrcIn 着色 —— 保留形状的 alpha、把全部 RGB 换成
            // contentColor。资产内部的逐元素 fill、内联 <style> 块、stroke 都无从漏出第二色，
            // 也不需要再靠 CSS 选择器去覆盖后代（那条路会被内联 style / 文档内 <style> 反超）。
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier.padding(4.dp)
        )
    }
}

@Composable
fun AutoAIIcon(
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    /**
     * 底色，语义同 [AIIcon]：缺省主题 `secondaryContainer`；匹配不到图标时退回字母头像
     * （[TextAvatar]，底色同样回退 `secondaryContainer`）。
     */
    color: Color = Color.Unspecified,
) {
    val path = remember(name) { computeAIIconByName(name) } ?: run {
        TextAvatar(
            text = name,
            modifier = modifier,
            loading = loading,
            color = color.takeOrElse { MaterialTheme.colorScheme.secondaryContainer },
        )
        return
    }
    AIIcon(
        path = path,
        name = name,
        modifier = modifier,
        loading = loading,
        color = color,
    )
}

@Preview
@Composable
private fun PreviewAutoAIIcon() {
    Column {
        AutoAIIcon("测试")
    }
}

@Composable
fun SiliconFlowPowerByIcon(modifier: Modifier = Modifier) {
    val darkMode = LocalDarkMode.current
    if (!darkMode) {
        AsyncImage(model = R.drawable.siliconflow_light, contentDescription = null, modifier = modifier)
    } else {
        AsyncImage(model = R.drawable.siliconflow_dark, contentDescription = null, modifier = modifier)
    }
}
