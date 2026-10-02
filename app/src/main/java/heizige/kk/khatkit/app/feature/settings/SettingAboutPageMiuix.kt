package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.onEach
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.effect.BgEffectBackground
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixPageMetrics
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.core.ui.icons.public
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import heizige.kk.khatkit.app.core.util.openUrl
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 关于页（Miuix）。结构与滚动逻辑照搬 KernelSU
 * `manager/app/src/main/java/me/weishu/kernelsu/ui/screen/about/AboutMiuix.kt`：
 *
 * - Logo 头图是**浮层**，不参与列表布局；列表首项是一个与头图等高的透明占位 spacer。
 * - 内容项用 `fillParentMaxHeight()`，保证内容不足一屏时页面依然可滚——这是 KSU 能滚动
 *   的关键，不需要额外塞底部留白（之前那版靠 `fillParentMaxHeight(0.72f)` 撑滚动量，
 *   系数调大调小都会顾此失彼：调大链接卡片被顶进顶栏底下裁掉首行，调小又滑不动）。
 * - 三段收起进度按各元素**实测的窗口内绝对 Y**分段计算（`iconY` / `projectNameY` /
 *   `versionCodeY`），滚过占位项后全部置 1。
 *
 * 与 KSU 的差异：KSU 给头图叠了 `textureBlur(DstIn)` 与动态取色背景，本页头图已在
 * [MiuixSettingsPage] 的模糊层内，且宿主无 `BgEffectBackground`，故只用透明度+缩放+位移。
 */
@Composable
fun SettingAboutPageMiuix() {
    val context = LocalContext.current
    val websiteUrl = stringResource(R.string.about_page_url_website)
    val githubUrl = stringResource(R.string.about_page_url_github)
    val licenseUrl = stringResource(R.string.about_page_url_license)

    val soundOptions = remember { listOf(R.raw.bingbingbing, R.raw.gangguan) }
    val soundEffectPlayer = remember(context) { SoundEffectPlayer(context) }
    DisposableEffect(soundEffectPlayer) {
        soundEffectPlayer.preload(*soundOptions.toIntArray())
        onDispose { soundEffectPlayer.release() }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Logo 头图实测高度，占位 spacer 与它对齐。
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    val spacerHeight = logoHeightDp + 218.dp
    val spacerPx = with(density) { spacerHeight.toPx() }

    // 整体滚动进度 0..1：流光淡出、顶栏底色淡入、标题延后淡入都用它。
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / spacerPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = scrollProgress,
        label = "aboutProgress",
    )

    // 三个元素底边的窗口内绝对 Y，用于分段计算收起进度。
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var iconY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }
    var iconProgress by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (listState.firstVisibleItemIndex > 0) {
                    iconProgress = 1f
                    projectNameProgress = 1f
                    versionCodeProgress = 1f
                    return@onEach
                }
                if (initialLogoAreaY == 0f && logoAreaY > 0f) initialLogoAreaY = logoAreaY
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY

                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val stage3TotalLength = projectNameY - iconY

                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress =
                    ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                projectNameProgress =
                    ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                iconProgress =
                    ((offset.toFloat() - stage1TotalLength - stage2TotalLength) / stage3TotalLength.coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
            }
            .collect { }
    }

    // 流光铺满整屏，滚过头图后随进度淡出；Scaffold 底色透明让底层透出来。
    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        alpha = { 1f - animatedProgress },
    ) {
    MiuixSettingsPage(
        title = stringResource(R.string.about_page_title),
        navigationIcon = { BackButton() },
        lazyListState = listState,
        // 让底层流光透出来（默认 surface 不透明会盖住 Canvas）
        containerColor = Color.Transparent,
        // 顶栏标题延后淡入：起始全透明（只剩返回按钮），滚过 35% 后才显现，与 KSU 的 titleColor 曲线一致
        titleAlpha = ((animatedProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
        smallTitleBar = true,
        // 顶栏底色随滚动淡入；非 null 时不套毛玻璃（底层就是流光）
        barColorAlpha = animatedProgress,
        overlay = {
            // ---- Logo 浮层（KSU：AboutContent 里 BgEffectBackground 的第一个子节点）----
            // 占满与占位项同高的一段，把 logo 块垂直居中在「卡片上方的空白区」里，
            // 而不是像 KSU 那样贴顶。内层单独包一层只为测量内容高度（logoHeightDp），
            // 避免外层高度反过来参与计算造成自增。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacerHeight)
                    .padding(
                        start = MiuixPageMetrics.HorizontalPadding,
                        end = MiuixPageMetrics.HorizontalPadding,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        with(density) { logoHeightDp = size.height.toDp() }
                    }
                    .onGloballyPositioned { coordinates ->
                        logoAreaY = coordinates.positionInWindow().y + coordinates.size.height
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .clipToBounds()
                        .graphicsLayer {
                            alpha = 1f - iconProgress
                            scaleX = 1f - iconProgress * 0.05f
                            scaleY = 1f - iconProgress * 0.05f
                        }
                        .onGloballyPositioned { coordinates ->
                            if (iconY != 0f) return@onGloballyPositioned
                            iconY = coordinates.positionInWindow().y + coordinates.size.height
                        }
                        .pointerInput(soundEffectPlayer) {
                            detectTapGestures { soundEffectPlayer.play(soundOptions.random()) }
                        },
                ) {
                    // ic_launcher 是 mipmap 下的位图/自适应图标（PNG），
                    // painterResource 只认 VectorDrawable，会抛 IllegalArgumentException，
                    // 所以这里走 Coil，和 Material 版 SettingAboutPage 的加载方式一致。
                    AsyncImage(
                        model = R.mipmap.ic_launcher,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(100.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 5.dp)
                        .graphicsLayer {
                            alpha = 1f - projectNameProgress
                            scaleX = 1f - projectNameProgress * 0.05f
                            scaleY = 1f - projectNameProgress * 0.05f
                        }
                        .onGloballyPositioned { coordinates ->
                            if (projectNameY != 0f) return@onGloballyPositioned
                            projectNameY = coordinates.positionInWindow().y + coordinates.size.height
                        },
                )
                Text(
                    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = colorScheme.onBackground,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = 1f - versionCodeProgress
                            scaleX = 1f - versionCodeProgress * 0.05f
                            scaleY = 1f - versionCodeProgress * 0.05f
                        }
                        .onGloballyPositioned { coordinates ->
                            if (versionCodeY != 0f) return@onGloballyPositioned
                            versionCodeY = coordinates.positionInWindow().y + coordinates.size.height
                        },
                )
            }
            }
        },
    ) {
        // ---- 透明占位 spacer：高度与头图对齐，KSU 的 logoSpacer ----
        // KSU 原式：logoHeightDp + 52.dp + logoPadding.top - scrollPadding.top + 126.dp，
        // 其中 logoPadding.top = innerPadding.top + 40.dp、scrollPadding.top = innerPadding.top，
        // 两项相消，恒等于 logoHeightDp + 218.dp。
        item(key = "logoSpacer") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(spacerHeight)
            )
        }

        // ---- 内容：fillParentMaxHeight 保证内容再少也可滚，KSU 的 about item ----
        item(key = "about") {
            Column(
                modifier = Modifier
                    .fillParentMaxHeight()
                    .padding(bottom = 12.dp)
            ) {
                KedgeCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MiuixPageMetrics.GroupSpacing),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    PreferenceArrow(
                        title = stringResource(R.string.about_page_website),
                        summary = websiteUrl,
                        icon = public,
                        onClick = { context.openUrl("$websiteUrl/") },
                    )
                    PreferenceArrow(
                        title = stringResource(R.string.about_page_github),
                        summary = githubUrl,
                        icon = code,
                        onClick = { context.openUrl(githubUrl) },
                    )
                    PreferenceArrow(
                        title = stringResource(R.string.about_page_license),
                        summary = licenseUrl,
                        icon = insertDriveFile,
                        onClick = { context.openUrl(licenseUrl) },
                    )
                }

                Spacer(
                    Modifier.height(
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }
    }
    }
}