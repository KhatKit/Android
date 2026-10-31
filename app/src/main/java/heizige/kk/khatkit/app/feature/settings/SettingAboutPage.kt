package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.easteregg.EmojiBurstHost
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.favorite
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.core.ui.icons.openInNew
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.icons.public
import heizige.kk.khatkit.app.core.ui.icons.rocketLaunch
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import heizige.kk.khatkit.app.core.ui.icons.download
import heizige.kk.khatkit.app.core.ui.icons.check
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialog
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.app.core.util.UpdateCheckResponse
import heizige.kk.khatkit.app.core.util.UiState
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import heizige.kk.khatkit.app.core.util.openUrl
import heizige.kk.khatkit.uikit.mochi.MochiSmallTopBar
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.shape.MochiSuperellipse
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.utils.rememberMochiHaptics

/**
 * 关于页（Mochi / iOS 26 分组样式）。
 *
 * 版面：固定的紧凑顶栏 + 头图（应用图标 / 名称 / 版本胶囊）+ 资源 / 更多两组 Mochi 分组卡片 + 页脚。
 * 头图不再跟滚动联动：早先那套「顶栏折叠 → 图标缩小淡出 → 标题淡入」的分段动画是旧
 * TopAppBar 折叠留下的遗产，页面内容本来就短，滚两下整块头图都在动，反而像没做完的动效，
 * 已连同滚动进度/三段区间/透明顶栏垫层一起删掉。
 *
 * 彩蛋保留：点图标撒 emoji + 播放音效 + 一次 Mochi 触觉反馈。
 */
@Composable
fun SettingAboutPage() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val haptics = rememberMochiHaptics()
    val cs = MochiTheme.colorScheme
    val scope = rememberCoroutineScope()
    val updateChecker = rememberAppEntryPoint().updateChecker()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    // 检查中转一圈，出结果时图标弹一下（Mochi 的弹簧手感）
    val spinTransition = rememberInfiniteTransition(label = "updateSpin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "updateSpinAngle",
    )
    val resultScale by animateFloatAsState(
        targetValue = if (updateResult != null) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
        label = "updateResultScale",
    )

    val soundOptions = remember { listOf(R.raw.bingbingbing, R.raw.gangguan) }
    val soundEffectPlayer = remember(context) { SoundEffectPlayer(context) }
    DisposableEffect(soundEffectPlayer) {
        soundEffectPlayer.preload(*soundOptions.toIntArray())
        onDispose { soundEffectPlayer.release() }
    }
    val emojiOptions = remember {
        listOf(
            "🎉", "✨", "🌟", "💫", "🎊", "🥳", "🎈", "🎆", "🎇", "🧨",
            "🌈", "🧧", "🎁", "🍬", "🍭", "🍉", "🍓", "🍒", "🍍", "🥭",
            "🐱", "🐶", "🦊", "🐼", "🦁", "🐯", "🐵", "🦄",
            "❤️", "🧡", "💛", "💚", "💙", "💜",
            "🇨🇳", "🌏", "🌍", "🌎",
            "🤗", "🤩", "😆", "😺", "😸", "🤡",
            "💡", "🔥", "💥", "🚀", "⭐", "🌙",
        )
    }

    val websiteUrl = stringResource(R.string.about_page_url_website)
    val githubUrl = stringResource(R.string.about_page_url_github)
    val licenseUrl = stringResource(R.string.about_page_url_license)
    val mochiUrl = "https://github.com/feice11/mochi-compose"

    var logoCenterPx by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        // 分组页底色：卡片（groupedContainer）比它亮一档，浅色/深色都成立
        containerColor = MochiDefaults.groupedBackground(),
        topBar = {
            Box {
                // 顶栏是固定的，直接铺一块实色底，正文从下面滚过去被完全盖住
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MochiDefaults.groupedBackground()),
                )
                MochiSmallTopBar(
                    title = stringResource(R.string.about_page_title),
                    navigationIcon = { BackButton() },
                )
            }
        },
    ) { innerPadding ->
        EmojiBurstHost(
            modifier = Modifier.fillMaxSize(),
            emojiOptions = emojiOptions,
            burstCount = 12,
        ) { onBurst ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 先吃 Scaffold 的 innerPadding 再挂滚动：反过来的话内边距会跟着内容一起滚走，
                    // 头图就会滑到顶栏底下露出来。
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                // 头图：图标 + 名称 + 版本胶囊，静态摆放，不跟滚动联动
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(156.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 图标外圈的主色光晕：浅色下是一圈很淡的绿，深色下把图标从黑底上托起来。
                        // 半径写死成圆的半径而不是留空（留空 = 无限大，整块几乎同色，等于没画）。
                        Box(
                            modifier = Modifier
                                .size(156.dp)
                                .drawBehind {
                                    val radius = size.minDimension / 2f
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                cs.primary.copy(alpha = 0.20f),
                                                cs.primary.copy(alpha = 0.06f),
                                                Color.Transparent,
                                            ),
                                            center = center,
                                            radius = radius,
                                        ),
                                        radius = radius,
                                        center = center,
                                    )
                                },
                        )
                        AsyncImage(
                            model = R.mipmap.ic_launcher,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(108.dp)
                                .clip(MochiSuperellipse(5f))
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInParent()
                                    val size = coordinates.size
                                    logoCenterPx = Offset(
                                        position.x + size.width / 2f,
                                        position.y + size.height / 2f,
                                    )
                                }
                                .pointerInput(soundEffectPlayer) {
                                    detectTapGestures {
                                        onBurst(logoCenterPx)
                                        haptics.tapOpen()
                                        soundEffectPlayer.play(soundOptions.random())
                                    }
                                },
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MochiTheme.textStyles.largeTitle,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(MochiTheme.shapes.full)
                            .background(MochiDefaults.fill())
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.about_page_version_format,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                            style = MochiTheme.textStyles.footnote,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (BuildConfig.DEBUG) {
                            Box(
                                modifier = Modifier
                                    .size(width = 1.dp, height = 10.dp)
                                    .background(cs.onSurfaceVariant.copy(alpha = 0.4f)),
                            )
                            Text(
                                text = "DEBUG",
                                style = MochiTheme.textStyles.footnote.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = cs.primary,
                            )
                        }
                    }
                }

                CardGroup(title = { Text("资源") }) {
                    item(
                        onClick = { context.openUrl(websiteUrl + "/") },
                        leadingContent = { AboutRowIcon(public) },
                        supportingContent = { Text("heizige.top") },
                        trailingContent = { ExternalLinkIcon() },
                        headlineContent = { Text(stringResource(R.string.about_page_website)) },
                    )
                    item(
                        onClick = {
                            if (!checkingUpdate) {
                                scope.launch {
                                    checkingUpdate = true
                                    val result = updateChecker.checkNow()
                                    checkingUpdate = false
                                    updateResult = when (result) {
                                        is UiState.Success -> if (result.data.hasUpdate) {
                                            UpdateCheckResult.Available(result.data)
                                        } else {
                                            UpdateCheckResult.UpToDate
                                        }
                                        is UiState.Error -> {
                                            Toast.show(
                                                "检查更新失败：${result.error.message ?: "未知错误"}",
                                                isError = true,
                                            )
                                            null
                                        }
                                        else -> null
                                    }
                                }
                            }
                        },
                        leadingContent = {
                            AboutRowIcon(
                                icon = refresh,
                                iconRotation = if (checkingUpdate) spinAngle else 0f,
                                iconScale = resultScale,
                            )
                        },
                        supportingContent = {
                            MochiBlurCrossfade(if (checkingUpdate) 1 else 0) { checking ->
                                Text(if (checking == 1) "正在检查新版本……" else "检查是否有新版本")
                            }
                        },
                        headlineContent = { Text("检测更新") },
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

                CardGroup(title = { Text("更多") }) {
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // 检测更新结果：Mochi 对话框（图标描绘 + 弹簧进出场），有新版本可直接更新
    val pending = updateResult
    MochiDialog(
        visible = pending != null,
        onDismissRequest = { updateResult = null },
        title = when (pending) {
            is UpdateCheckResult.Available -> "发现新版本 ${pending.info.latestVersion}"
            UpdateCheckResult.UpToDate -> "已是最新版本"
            null -> ""
        },
        message = when (pending) {
            is UpdateCheckResult.Available ->
                pending.info.description.takeIf { it.isNotBlank() } ?: "有新版本可以更新。"
            UpdateCheckResult.UpToDate -> "当前已是最新版本，无需更新。"
            null -> null
        },
        icon = when (pending) {
            is UpdateCheckResult.Available -> {
                { Icon(download, null, Modifier.size(30.dp)) }
            }
            UpdateCheckResult.UpToDate -> {
                { Icon(check, null, Modifier.size(30.dp)) }
            }
            null -> null
        },
        actions = when (pending) {
            is UpdateCheckResult.Available -> persistentListOf(
                MochiDialogAction(
                    label = "稍后",
                    role = MochiDialogActionRole.Cancel,
                    onClick = { updateResult = null },
                ),
                MochiDialogAction(
                    label = "立即更新",
                    role = MochiDialogActionRole.Primary,
                    onClick = {
                        pending.info.let { updateChecker.downloadUpdate(context, it) }
                        updateResult = null
                    },
                ),
            )
            else -> persistentListOf(
                MochiDialogAction(
                    label = "知道了",
                    role = MochiDialogActionRole.Primary,
                    onClick = { updateResult = null },
                ),
            )
        },
    )
}

/** 行首图标块：28dp 连续圆角 + 12% 主色底（iOS 26 设置行语言），图标 17dp 主色。 */
@Composable
private fun AboutRowIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconRotation: Float = 0f,
    iconScale: Float = 1f,
) {
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
            modifier = Modifier
                .size(17.dp)
                .graphicsLayer {
                    rotationZ = iconRotation
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = cs.primary,
        )
    }
}

/** 「检测更新」的结果：有新版本 / 已是最新。 */
private sealed interface UpdateCheckResult {
    data class Available(val info: UpdateCheckResponse) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
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
