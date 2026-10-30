package heizige.kk.khatkit.app.core.ui.components.nav

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.icons.arrowBack



/**
 * 返回按钮，双风格：
 *
 * - Miuix：Miuix 原生 [MiuixIconButton]，无 tonal 底色（对齐 Miuix 顶栏观感）
 * - MD3Exp：MD3 [FilledTonalIconButton]，底色取列表卡色
 *
 * 全 App 60+ 处页面顶栏都用它，所以按风格在这里分流即可，
 * 不需要每个页面各写一份。
 */
@Composable
fun BackButton(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val navController = LocalNavController.current
    val back = { onClick?.invoke() ?: navController.popBackStack() }
    // U1c：Miuix 分支已删，只剩 MD3 形态（返回行为不变，仍是 navController.popBackStack）。
    run {
        FilledTonalIconButton(
            onClick = back,
            modifier = modifier,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = CustomColors.listItemColors.containerColor,
            ),
        ) {
            Icon(
                imageVector = arrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
    }
}
