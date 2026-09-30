package heizige.kk.khatkit.app.feature.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 预设主题色列表（供 Miuix 版复用）。 */
val ThemeCustomColors get() = heizige.kk.khatkit.app.core.ui.theme.PresetThemes

/**
 * 主题色分组的 Miuix 版：动态取色开关 + 预设色点（沿用 MD3 版的 [PresetThemeColorDots]，
 * 它是纯 Compose 绘制，与风格无关）。
 */
@Composable
fun ThemeColorSettingGroupMiuix(
    dynamicColor: Boolean,
    themeId: String,
    onUpdateDynamicColor: (Boolean) -> Unit,
    onSelectTheme: (String) -> Unit,
    onCustomColorClick: () -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorEnabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PreferenceSwitch(
            title = stringResource(R.string.greeting_settings_dynamic_color),
            summary = stringResource(R.string.greeting_settings_dynamic_color_desc),
            icon = palette,
            enabled = dynamicColorEnabled,
            checked = dynamicColorEnabled && dynamicColor,
            onCheckedChange = { onUpdateDynamicColor(it) },
        )
        AnimatedVisibility(
            visible = !dynamicColor,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                )
            ) + fadeOut(),
        ) {
            PresetThemeColorDots(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(
                        MiuixTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(vertical = 12.dp),
                selectedThemeId = themeId,
                onSelectTheme = onSelectTheme,
                onCustomColorClick = onCustomColorClick,
            )
        }
    }
}
