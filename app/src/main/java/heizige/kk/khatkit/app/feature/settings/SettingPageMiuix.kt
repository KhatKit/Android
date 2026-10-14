package heizige.kk.khatkit.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceDropdown
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.addPhotoAlternate
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.book2
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.campaign
import heizige.kk.khatkit.app.core.ui.icons.celebration
import heizige.kk.khatkit.app.core.ui.icons.dashboard
import heizige.kk.khatkit.app.core.ui.icons.database
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.favorite
import heizige.kk.khatkit.app.core.ui.icons.inventory2
import heizige.kk.khatkit.app.core.ui.icons.lightMode
import heizige.kk.khatkit.app.core.ui.icons.psychology
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.settings as settingsIcon
import heizige.kk.khatkit.app.core.ui.icons.share as shareIcon
import heizige.kk.khatkit.app.core.ui.icons.shelves
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.util.openUrl
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.datastore.isNotConfigured
import heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 设置主页的 Miuix 风格版 —— Miuix 用户打开设置看到的第一屏。
 *
 * 分组顺序与 MD3 版完全一致：颜色模式 / 通用 / 模型与服务 / 数据 / 关于。
 */
@Composable
fun SettingPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val entryPoint = rememberAppEntryPoint()
    val filesManager = entryPoint.filesManager()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val shareText = stringResource(R.string.setting_page_share_text)
    val share = stringResource(R.string.setting_page_share)
    val noShareApp = stringResource(R.string.setting_page_no_share_app)
    var showQQGroupSheet by remember { mutableStateOf(false) }

    val storageState by produceState(-1 to 0L) {
        value = filesManager.countChatFiles()
    }

    MiuixSettingsPage(
        title = stringResource(R.string.settings),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
    ) {
        if (settings.isNotConfigured()) {
            item {
                ProviderConfigWarningCard(navController)
            }
        }

        miuixGroup {
            // #3 外观三选（跟随系统/浅色/深色）：与 MD3 主页的外观三选卡片
            // （`SettingPage.kt` 的 selectedColorModeText / Select.optionToString）引用同一组
            // 资源键（setting_page_color_mode / _system / _light / _dark）、同一持久化字段
            // （rememberColorMode 读写的就是 SharedPreferences 的 "colorMode"，缺省
            // ColorMode.SYSTEM ⇒ 「跟随系统」）。MD3 用 Select，Miuix 用自家的
            // OverlayDropdownPreference，条目顺序与默认值逐字相同。
            val colorMode = rememberColorMode()
            PreferenceDropdown(
                title = stringResource(R.string.setting_page_color_mode),
                summary = colorModeText(colorMode.value),
                items = ColorMode.entries.map { colorModeText(it) },
                selectedIndex = ColorMode.entries.indexOf(colorMode.value),
                onSelectedIndexChange = { colorMode.value = ColorMode.entries[it] },
                icon = lightMode,
            )
            // 「主题」hub 与上面的外观三选并存（用户裁定「并集补齐，两侧条目完全一致」，
            // 不是二选一）：主题模式（跟随系统/浅色/深色）连同动态取色、主题配色、AMOLED
            // 一起收进「设置 → 主题 → 主题偏好设置」。
            PreferenceArrow(
                title = stringResource(R.string.setting_page_themes),
                summary = stringResource(R.string.setting_page_themes_desc),
                icon = dashboard,
                onClick = { navController.navigate(Screen.SettingThemes) },
            )
        }

        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_preferences),
                summary = stringResource(R.string.setting_page_preferences_desc),
                icon = settingsIcon,
                onClick = { navController.navigate(Screen.SettingPreferences) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_assistant),
                summary = stringResource(R.string.setting_page_assistant_desc),
                icon = search,
                onClick = { navController.navigate(Screen.Assistant) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_extensions),
                summary = stringResource(R.string.setting_page_extensions_desc),
                icon = inventory2,
                onClick = { navController.navigate(Screen.Extensions) },
            )
        }

        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_default_model),
                summary = stringResource(R.string.setting_page_default_model_desc),
                icon = autoAwesome,
                onClick = { navController.navigate(Screen.SettingModels) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_providers),
                icon = psychology,
                onClick = { navController.navigate(Screen.SettingProvider) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_search_service),
                icon = travelExplore,
                onClick = { navController.navigate(Screen.SettingSearch) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_tts_service),
                summary = stringResource(R.string.setting_page_tts_service_desc),
                icon = campaign,
                onClick = { navController.navigate(Screen.SettingSpeech) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_mcp),
                icon = dns,
                onClick = { navController.navigate(Screen.SettingMcp) },
            )
            // #5 轨迹审计：目标与 MD3 `SettingPage.kt:237-242` 完全一致（Screen.TraceAudit），
            // 文案改用同一组新资源键（原 MD3 侧是硬编码中文）。
            PreferenceArrow(
                title = stringResource(R.string.setting_page_trace_audit),
                summary = stringResource(R.string.setting_page_trace_audit_desc),
                icon = bolt,
                onClick = { navController.navigate(Screen.TraceAudit) },
            )
            PreferenceArrow(
                title = "探索市场",
                summary = "一站式浏览工具、技能、MCP、模型能力与脚本卡片",
                icon = inventory2,
                onClick = { navController.navigate(Screen.ExploreMarket) },
            )
            PreferenceArrow(
                title = "套餐激活",
                summary = "激活 KhatKitHub 套餐、查看额度，并创建 AI 网关供应商",
                icon = favorite,
                onClick = { navController.navigate(Screen.SettingPackage) },
            )
            PreferenceArrow(
                title = "自动化触发器",
                summary = "卡片在定时、通知、应用启动、充电时自动运行",
                icon = bolt,
                onClick = { navController.navigate(Screen.SettingTriggers) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_web_server),
                summary = stringResource(R.string.setting_page_web_server_desc),
                icon = dns,
                onClick = { navController.navigate(Screen.SettingWeb) },
            )
        }

        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_data_backup),
                summary = stringResource(R.string.setting_page_data_backup_desc),
                icon = database,
                onClick = { navController.navigate(Screen.Backup) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_chat_storage),
                summary = if (storageState.first == -1) {
                    stringResource(R.string.calculating)
                } else {
                    stringResource(
                        R.string.setting_page_chat_storage_desc,
                        storageState.first,
                        storageState.second / 1024 / 1024.0,
                    )
                },
                icon = addPhotoAlternate,
                onClick = { navController.navigate(Screen.SettingFiles) },
            )
        }

        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_about),
                icon = celebration,
                onClick = { navController.navigate(Screen.SettingAbout) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_documentation),
                summary = stringResource(R.string.setting_page_documentation_desc),
                icon = book2,
                onClick = {
                    val docUrl = if (java.util.Locale.getDefault().language == "zh") {
                        "https://docs.rikka-ai.com/zh/introduction"
                    } else {
                        "https://docs.rikka-ai.com/introduction"
                    }
                    context.openUrl(docUrl)
                },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_request_logs),
                summary = stringResource(R.string.setting_page_request_logs_desc),
                icon = shelves,
                onClick = { navController.navigate(Screen.Log) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_donate),
                summary = stringResource(R.string.setting_page_donate_desc),
                icon = favorite,
                onClick = { navController.navigate(Screen.SettingDonate) },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_page_share),
                summary = stringResource(R.string.setting_page_share_desc),
                icon = shareIcon,
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, shareText)
                    try {
                        context.startActivity(Intent.createChooser(intent, share))
                    } catch (e: ActivityNotFoundException) {
                        android.widget.Toast
                            .makeText(context, noShareApp, android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                },
            )
        }
    }

    if (showQQGroupSheet) {
        QQGroupBottomSheet(onDismiss = { showQQGroupSheet = false })
    }
}

/**
 * 外观三选（#3）的条目文案 —— 与 MD3 `SettingPage.kt` 的外观三选引用的是同一组资源键。
 */
@Composable
private fun colorModeText(mode: ColorMode): String = stringResource(
    when (mode) {
        ColorMode.SYSTEM -> R.string.setting_page_color_mode_system
        ColorMode.LIGHT -> R.string.setting_page_color_mode_light
        ColorMode.DARK -> R.string.setting_page_color_mode_dark
    }
)
