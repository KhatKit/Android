package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.lightMode
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle

/**
 * 主题入口页：把原先散在「设置 → 颜色模式」和「设置 → 偏好设置 → 主题」两处的
 * 主题相关页面收拢到这一个 hub 之下。
 *
 * 两个子项的分工：
 * - **主题管理**（[Screen.SettingTheme] → [SettingThemePage]）：自定义主题的增删改与
 *   导入导出。此前 `SettingThemePage` 已注册路由但全仓没有任何地方跳转到，是个不可达
 *   页面，这次顺便接上。
 * - **主题偏好设置**（[Screen.SettingPreferencesTheme] → [SettingPreferencesThemePage]）：
 *   主题模式（跟随系统/浅色/深色）、动态取色与主题配色、AMOLED 暗色模式。原来设置主页上
 *   那个「颜色模式」下拉就是它的一部分，移进这里后仍然可以改颜色模式，只是多一跳。
 */
@Composable
fun SettingThemesPage() {
    // Miuix 走双文件（KernelSU SettingsMaterial/SettingsMiuix 架构）
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingThemesPageMiuix()
        return
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.setting_page_themes),
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        onClick = { navController.navigate(Screen.SettingTheme) },
                        leadingContent = { Icon(palette, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_theme_manage)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_theme_manage_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
                        leadingContent = { Icon(lightMode, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_theme_preferences)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_theme_desc)) },
                    )
                }
            }
        }
    }
}