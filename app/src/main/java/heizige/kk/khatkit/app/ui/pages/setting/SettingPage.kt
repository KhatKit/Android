package heizige.kk.khatkit.app.ui.pages.setting

import heizige.kk.kedge.components.KedgeOptionItem
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.isNotConfigured
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.Select
import heizige.kk.khatkit.app.core.ui.icons.DiscordIcon
import heizige.kk.khatkit.app.core.ui.icons.TencentQQIcon
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.Navigator
import heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.joinQQGroup
import heizige.kk.khatkit.app.core.util.openUrl
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khromia.components.AnimatedRadioItem
import heizige.kk.khromia.components.ExpandableOptionItem
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.util.writeClipboardText
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.icons.addPhotoAlternate
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.book2
import heizige.kk.khatkit.app.core.ui.icons.campaign
import heizige.kk.khatkit.app.core.ui.icons.celebration
import heizige.kk.khatkit.app.core.ui.icons.database
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.favorite
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.khatkit.app.core.ui.icons.inventory2
import heizige.kk.khatkit.app.core.ui.icons.lightMode
import heizige.kk.khatkit.app.core.ui.icons.psychology
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.khatkit.app.core.ui.icons.settings as settingsIcon
import heizige.kk.khatkit.app.core.ui.icons.share as shareIcon
import heizige.kk.khatkit.app.core.ui.icons.shelves
import heizige.kk.khatkit.app.core.ui.icons.travelExplore
import heizige.kk.khatkit.app.core.ui.icons.warning
import heizige.kk.khatkit.app.core.ui.icons.wavingHand

@Composable
fun SettingPage(vm: SettingVM = hiltViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()

    if (settings.launchCount > 100 && (settings.launchCount - settings.sponsorAlertDismissedAt) >= 50) {
        AppAlertDialog(
            onDismissRequest = {
                vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
            },
            icon = { Icon(wavingHand, null) },
            title = { Text(stringResource(R.string.setting_page_sponsor_alert_title)) },
            text = { Text(stringResource(R.string.setting_page_sponsor_alert_desc)) },
            confirmButton = {
                Button(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                    navController.navigate(Screen.SettingDonate)
                }) {
                    Text(stringResource(R.string.setting_page_sponsor_alert_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                }) {
                    Text(stringResource(R.string.setting_page_sponsor_alert_dismiss))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(navController)
                }
            }

            item("themeMode") {
                val colorMode = rememberColorMode()
                val selectedColorModeText = when (colorMode.value) {
                    ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                    ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                    ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        leadingContent = { Icon(lightMode, null) },
                        trailingContent = {
                            Select(
                                options = ColorMode.entries,
                                selectedOption = colorMode.value,
                                onOptionSelected = { colorMode.value = it },
                                optionToString = {
                                    when (it) {
                                        ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                        ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                        ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                    }
                                },
                                modifier = Modifier.width(150.dp)
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_color_mode)) },
                        supportingContent = { Text(selectedColorModeText) },
                    )
                }
            }

            item("generalSettings") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_general_settings)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferences) },
                        leadingContent = { Icon(settingsIcon, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Assistant) },
                        leadingContent = { Icon(search, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_assistant)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Extensions) },
                        leadingContent = { Icon(inventory2, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_extensions_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_extensions)) },
                    )
                }
            }

            item("modelServices") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_model_and_services)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingModels) },
                        leadingContent = { Icon(autoAwesome, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_default_model)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingProvider) },
                        leadingContent = { Icon(psychology, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSearch) },
                        leadingContent = { Icon(travelExplore, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_search_service)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSpeech) },
                        leadingContent = { Icon(campaign, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingMcp) },
                        leadingContent = { Icon(dns, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_mcp)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.KhatKitMarket) },
                        leadingContent = { Icon(inventory2, null) },
                        supportingContent = { Text("浏览、安装脚本卡片，让 AI 能操作手机") },
                        headlineContent = { Text("KhatKit 卡片市场") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPackage) },
                        leadingContent = { Icon(favorite, null) },
                        supportingContent = { Text("激活 KhatKitHub 套餐、查看额度，并创建 AI 网关供应商") },
                        headlineContent = { Text("套餐 / 激活") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingTriggers) },
                        leadingContent = { Icon(bolt, null) },
                        supportingContent = { Text("卡片在定时、通知、应用启动、充电时自动运行") },
                        headlineContent = { Text("自动化触发器") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingWeb) },
                        leadingContent = { Icon(dns, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_web_server_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_web_server)) },
                    )
                }
            }

            item("dataSettings") {
                val storageState by produceState(-1 to 0L) {
                    value = filesManager.countChatFiles()
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_data_settings)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Backup) },
                        leadingContent = { Icon(database, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_data_backup)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingFiles) },
                        leadingContent = { Icon(addPhotoAlternate, null) },
                        supportingContent = {
                            if (storageState.first == -1) {
                                Text(stringResource(R.string.calculating))
                            } else {
                                Text(
                                    stringResource(
                                        R.string.setting_page_chat_storage_desc,
                                        storageState.first,
                                        storageState.second / 1024 / 1024.0
                                    )
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_chat_storage)) },
                    )
                }
            }

            item("aboutSettings") {
                val context = LocalContext.current
                val shareText = stringResource(R.string.setting_page_share_text)
                val share = stringResource(R.string.setting_page_share)
                val noShareApp = stringResource(R.string.setting_page_no_share_app)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_about)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingAbout) },
                        leadingContent = { Icon(celebration, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_about_desc)) },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                var showQQGroupSheet by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showQQGroupSheet = true }
                                ) {
                                    Icon(
                                        imageVector = TencentQQIcon,
                                        contentDescription = "QQ",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (showQQGroupSheet) {
                                    QQGroupBottomSheet(
                                        onDismiss = { showQQGroupSheet = false }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        context.openUrl("https://discord.gg/9weBqxe5c4")
                                    }
                                ) {
                                    Icon(
                                        imageVector = DiscordIcon,
                                        contentDescription = "Discord",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_about)) },
                    )
                    item(
                        onClick = {
                            val docUrl = if (java.util.Locale.getDefault().language == "zh") {
                                "https://docs.rikka-ai.com/zh/introduction"
                            } else {
                                "https://docs.rikka-ai.com/introduction"
                            }
                            context.openUrl(docUrl)
                        },
                        leadingContent = { Icon(book2, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_documentation_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_documentation)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Log) },
                        leadingContent = { Icon(shelves, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_request_logs_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_request_logs)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingDonate) },
                        leadingContent = { Icon(favorite, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_donate_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_donate)) },
                    )
                    item(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            intent.putExtra(Intent.EXTRA_TEXT, shareText)
                            try {
                                context.startActivity(Intent.createChooser(intent, share))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
                            }
                        },
                        leadingContent = { Icon(shareIcon, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_share_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_share)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigWarningCard(navController: Navigator) {
    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            KedgeOptionItem(
                onClick = {},
                backgroundColor = Color.Transparent,
                leadingContent = {
                    Icon(warning, null)
                },
                titleContent = { Text(stringResource(R.string.setting_page_config_api_title)) },
                supportingContent = { Text(stringResource(R.string.setting_page_config_api_desc)) },
            )

            TextButton(
                onClick = {
                    navController.navigate(Screen.SettingProvider)
                }
            ) {
                Text(stringResource(R.string.setting_page_config))
            }
        }
    }
}

private data class QQGroup(
    val name: String,
    val key: String? = null,
    val number: String? = null,
    val icon: ImageVector = TencentQQIcon,
)

private val QQ_GROUPS = listOf(
    QQGroup("KhatKit 一群", "4POE46u9e_zoy1TkNfWdCvueR9CKFJdk"),
    QQGroup("KhatKit 二群", "Qsm0whzbPsm1UyNpR683ulLyMZ2Pqrw0"),
    QQGroup("KhatKit 三群", "Qc9oP-9tXioZeQEvEvI2_owWtBAIx3lS"),
    QQGroup("抖音一群", number = "569655479852", icon = groups),
)

@Composable
private fun QQGroupBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    PrimaryBottomSheet(
        visible = true,
        title = "加入群聊",
        imageVector = groups,
        onDismiss = onDismiss,
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val cards = heizige.kk.khatkit.app.core.ui.theme.listCardStyle()
            QQ_GROUPS.forEachIndexed { index, group ->
                KedgeOptionItem(
                    shape = cards.indexedShape(index, QQ_GROUPS.size),
                    onClick = {
                        if (group.number != null) {
                            context.writeClipboardText(group.number)
                            Toast.makeText(context, "群号已复制", Toast.LENGTH_SHORT).show()
                        } else {
                            context.joinQQGroup(group.key)
                        }
                        dismiss()
                    },
                    leadingContent = {
                        Icon(
                            imageVector = group.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    titleContent = { Text(group.name) },
                    supportingContent = group.number?.let { number ->
                        { Text(number) }
                    },
                )
            }
        }
    }
}
