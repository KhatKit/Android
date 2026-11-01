package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.easteregg.EmojiBurstHost
import heizige.kk.khatkit.app.core.ui.components.easteregg.rememberEmojiBurstState
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.download
import heizige.kk.khatkit.app.core.ui.icons.favorite
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.core.ui.icons.openInNew
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.icons.public
import heizige.kk.khatkit.app.core.ui.icons.rocketLaunch
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.UiState
import heizige.kk.khatkit.app.core.util.openUrl
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.uikit.mochi.MochiStatusLevel
import heizige.kk.khatkit.uikit.mochi.MochiStatusTag
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/**
 * 关于页。
 *
 * 脚手架与其余 49 个设置子页一致：KedgeSettingsPageScaffold（Mochi 紧凑标题栏 + 分组页底色）
 * + LazyColumn + CardGroup。上一版是手写的 Scaffold + MochiSmallTopBar + verticalScroll，
 * 于是只有这一页：顶栏不是 MochiTitleBar（返回按钮少一层栏按钮底）、正文用 26dp 间距和 16dp 边距
 * （别的页是 16dp 间距、8+8 边距，卡片离屏幕 16dp）、滚动手感没有滚动边缘渐隐 —— 现在全部对齐。
 *
 * 版面（自上而下）：
 * 1. 头图：应用图标（彩蛋入口，见 [AboutHero]）+ 名称 + 一句话 + 版本胶囊。
 * 2. 更新：独立的 [AboutUpdateCard]（检查更新从「资源」分组里拿出来了）。
 * 3. 资源 / 更多：两组 Mochi 分组卡片。
 * 4. 页脚：版本、Mochi 署名、彩蛋解锁后追加的徽章和「打开隐藏终端」入口。
 *
 * 彩蛋：连点图标十下（或长按蓄力）解锁，落点是 [AboutTerminalPage] —— 一个不在任何菜单里的
 * 隐藏终端，能敲 help / version / mochi / egg / clear / exit。
 */
@Composable
fun SettingAboutPage() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val updateChecker = rememberAppEntryPoint().updateChecker()
    val egg = rememberAboutEggState()
    val burst = rememberEmojiBurstState()

    var updateState by remember { mutableStateOf<AboutUpdateState>(AboutUpdateState.Idle) }
    var showUpdateDetail by remember { mutableStateOf(false) }

    val emojiOptions = remember {
        listOf(
            "🎉", "✨", "🌟", "💫", "🎊", "🥳", "🎈", "🎆", "🎇", "🧨",
            "🌈", "🧧", "🎁", "🍬", "🍭", "🍡", "🍉", "🍓", "🍒", "🍍",
            "🐱", "🐶", "🦊", "🐼", "🦁", "🐯", "🐵", "🦄",
            "❤️", "🧡", "💛", "💚", "💙", "💜",
            "🤗", "🤩", "😆", "😺", "😸", "🤡",
            "💡", "🔥", "💥", "🚀", "⭐", "🌙",
        )
    }

    val websiteUrl = stringResource(R.string.about_page_url_website)
    val githubUrl = stringResource(R.string.about_page_url_github)
    val licenseUrl = stringResource(R.string.about_page_url_license)
    val mochiUrl = "https://github.com/feice11/mochi-compose"

    fun checkUpdate() {
        if (updateState is AboutUpdateState.Checking) return
        scope.launch {
            updateState = AboutUpdateState.Checking
            updateState = when (val result = updateChecker.checkNow()) {
                is UiState.Success -> {
                    if (result.data.hasUpdate) {
                        AboutUpdateState.Available(result.data)
                    } else {
                        Toast.show("已是最新版本", isError = false)
                        AboutUpdateState.UpToDate
                    }
                }
                is UiState.Error -> {
                    val message = result.error.message ?: "未知错误"
                    Toast.show("检查更新失败：" + message, isError = true)
                    AboutUpdateState.Failed(message)
                }
                else -> AboutUpdateState.Idle
            }
        }
    }

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.about_page_title),
        scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    ) { innerPadding ->
        // 粒子宿主铺满正文区：emoji 从图标处炸开、落到页面底部；正文照常滚，粒子不跟着滚
        EmojiBurstHost(
            state = burst,
            emojiOptions = emojiOptions,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(
                    start = 8.dp,
                    top = PageMetrics.TopContentPadding,
                    end = 8.dp,
                    bottom = 8.dp + PageMetrics.BottomContentPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("hero") {
                    AboutHero(
                        state = egg,
                        burst = burst,
                        onOpenTerminal = { navController.navigate(Screen.AboutTerminal) },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                item("update") {
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        AboutSectionHeader("更新")
                        AboutUpdateCard(
                            state = updateState,
                            onCheck = { checkUpdate() },
                            onOpenDetail = { showUpdateDetail = true },
                        )
                    }
                }

                item("resources") {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text("资源") },
                    ) {
                        item(
                            onClick = { context.openUrl(websiteUrl) },
                            leadingContent = { AboutRowIcon(public) },
                            supportingContent = { Text("heizige.top") },
                            trailingContent = { ExternalLinkIcon() },
                            headlineContent = { Text(stringResource(R.string.about_page_website)) },
                        )
                        item(
                            onClick = { context.openUrl(githubUrl) },
                            leadingContent = { AboutRowIcon(code) },
                            supportingContent = { Text("github.com/rikkahub") },
                            trailingContent = { ExternalLinkIcon() },
                            headlineContent = { Text(stringResource(R.string.about_page_github)) },
                        )
                        item(
                            onClick = { context.openUrl(licenseUrl) },
                            leadingContent = { AboutRowIcon(insertDriveFile) },
                            supportingContent = { Text("GNU AGPL v3") },
                            trailingContent = { ExternalLinkIcon() },
                            headlineContent = { Text(stringResource(R.string.about_page_license)) },
                        )
                    }
                }

                item("more") {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text("更多") },
                    ) {
                        item(
                            onClick = { navController.navigate(Screen.SettingDonate) },
                            leadingContent = { AboutRowIcon(favorite) },
                            supportingContent = { Text("支持 KhatKit 的持续开发") },
                            headlineContent = { Text("赞助作者") },
                            chevron = true,
                        )
                        item(
                            onClick = { context.openUrl(githubUrl) },
                            leadingContent = { AboutRowIcon(rocketLaunch) },
                            supportingContent = { Text("github.com/rikkahub") },
                            trailingContent = { ExternalLinkIcon() },
                            headlineContent = { Text("上游项目 RikkaHub") },
                        )
                        item(
                            onClick = { context.openUrl(mochiUrl) },
                            leadingContent = { AboutRowIcon(palette) },
                            supportingContent = { Text("github.com/feice11") },
                            trailingContent = { ExternalLinkIcon() },
                            headlineContent = { Text("界面 Mochi Compose") },
                        )
                    }
                }

                item("footer") {
                    AboutFooter(
                        egg = egg,
                        onOpenTerminal = { navController.navigate(Screen.AboutTerminal) },
                    )
                }
            }
        }
    }

    // 有新版本：Mochi 对话框看说明、下载（「已是最新」走 Toast，不再弹框打断）
    val available = (updateState as? AboutUpdateState.Available)?.info
    MochiDialog(
        visible = showUpdateDetail && available != null,
        onDismissRequest = { showUpdateDetail = false },
        title = if (available != null) "发现新版本 " + available.latestVersion else "",
        message = available?.description?.takeIf { it.isNotBlank() } ?: "有新版本可以更新。",
        icon = when {
            available != null -> {
                { Icon(download, null, Modifier.size(30.dp)) }
            }
            else -> null
        },
        actions = persistentListOf(
            MochiDialogAction(
                label = "稍后",
                role = MochiDialogActionRole.Cancel,
                onClick = { showUpdateDetail = false },
            ),
            MochiDialogAction(
                label = "立即更新",
                role = MochiDialogActionRole.Primary,
                onClick = {
                    available?.let { updateChecker.downloadUpdate(context, it) }
                    showUpdateDetail = false
                },
            ),
        ),
    )
}

/**
 * 分组小标题：与 CardGroup 的 title 槽同一套（navTitle 字阶、次要色、行文字起点、下方 HeaderGap），
 * 让独立的更新卡片外面也有一行自己的分组名，纵向节奏和其它两组一致。
 */
@Composable
private fun AboutSectionHeader(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MochiListDefaults.TextRowPadding,
                end = MochiListDefaults.TextRowPadding,
                bottom = MochiListDefaults.HeaderGap,
            ),
    ) {
        Text(
            text = text,
            style = MochiTheme.textStyles.navTitle,
            color = MochiListDefaults.colors().secondary,
        )
    }
}

/** 页脚：版本 + 署名；彩蛋解锁后追加一枚徽章和最高连击。 */
@Composable
private fun AboutFooter(egg: AboutEggState, onOpenTerminal: () -> Unit) {
    val cs = MochiTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "KhatKit " + BuildConfig.VERSION_NAME,
            style = MochiTheme.textStyles.footnote,
            color = cs.onSurfaceVariant,
        )
        Text(
            text = "界面由 Mochi Compose 驱动 🍡",
            style = MochiTheme.textStyles.footnote,
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
        )
        AnimatedVisibility(
            visible = egg.unlocked,
            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            Column(
                modifier = Modifier.padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MochiStatusTag(label = "彩蛋猎人", level = MochiStatusLevel.Success)
                    if (egg.bestCombo > 0) {
                        Text(
                            text = "最高连击 " + egg.bestCombo + " 次",
                            style = MochiTheme.textStyles.footnote,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                // 解锁之后把入口留在页脚：不用每次都再点十下才能回到终端
                MochiTextButton(onClick = onOpenTerminal) {
                    Text(
                        text = "打开隐藏终端",
                        style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.primary,
                    )
                }
            }
        }
    }
}

/** 行首图标块：28dp 连续圆角 + 12% 主色底（iOS 26 设置行语言），图标 17dp 主色。 */
@Composable
private fun AboutRowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val cs = MochiTheme.colorScheme
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(MochiRoundedRectangle(8.dp))
            .background(cs.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = cs.primary,
        )
    }
}

/** 外链行尾：统一一枚「打开外链」图标，和设置里进二级页的箭头区分开。 */
@Composable
private fun ExternalLinkIcon() {
    Icon(
        imageVector = openInNew,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    )
}
