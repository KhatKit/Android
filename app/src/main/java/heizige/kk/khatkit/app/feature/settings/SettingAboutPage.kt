package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.easteregg.EmojiBurstHost
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.kedge.adaptive.KedgeSmallTopBar
import heizige.kk.khatkit.app.core.ui.effect.BgEffectBackground
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.core.ui.icons.public
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import heizige.kk.khatkit.app.core.util.openUrl
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle

/**
 * 关于页（Kedge MD3Exp）。
 *
 * 结构对齐 Miuix 版与 KernelSU `ui/screen/about/AboutMiuix.kt`：顶部大 logo 头图，
 * 滚动时按「版本号 → 应用名 → logo」的顺序分三段淡出、缩小并上移，头图背后叠一层
 * 词幕（Kifranei/lyricon）的流光着色器背景并随滚动淡出。两者共用同一套区间映射，
 * 切换风格时观感一致。
 *
 * 与 Miuix 版的差异仅在彩蛋：这里保留点击 logo 撒 emoji 的 [EmojiBurstHost]。
 */
@Composable
fun SettingAboutPage() {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingAboutPageMiuix()
        return
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
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
            "💡", "🔥", "💥", "🚀", "⭐", "🌙"
        )
    }

    val listState = rememberLazyListState()
    // 头图高度即滚动行程：滚过这么多像素，头图完全收起
    val heroHeight = 232.dp
    val heroPx = with(LocalDensity.current) { heroHeight.toPx() }
    // 0f=完全展开，1f=完全收起。滚到第二项时直接拉满，之后不再变化。
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / heroPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = scrollProgress,
        label = "aboutHeroProgress",
    )

    // 三段区间：版本号先走、应用名次之、logo 最后，与 KSU 的顺序一致
    fun stage(start: Float, end: Float): Float =
        ((animatedProgress - start) / (end - start)).coerceIn(0f, 1f)

    val logoProgress = stage(0.00f, 0.55f)
    val nameProgress = stage(0.18f, 0.78f)
    val versionProgress = stage(0.38f, 1.00f)
    // 顶栏标题延后淡入：起始全透明（只剩返回按钮），滚过 35% 后才开始显现
    // （与 Miuix 版同一曲线，保持两种风格一致）。
    val titleAlpha = ((animatedProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)

    val websiteUrl = stringResource(R.string.about_page_url_website)
    val githubUrl = stringResource(R.string.about_page_url_github)
    val licenseUrl = stringResource(R.string.about_page_url_license)

    var logoCenterPx by remember { mutableStateOf(Offset.Zero) }
    // 流光放在 Scaffold **外层**：Scaffold 的 containerColor 不透明，
    // 放在 content 槽里会被它整块盖住（KSU / 词幕都是把效果背景放在最外层）。
    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        alpha = { 1f - animatedProgress },
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // 小标题栏（KSU AboutMiuix 用 SmallTopAppBar）：没有折叠大标题，
            // 标题不会同时出现在两个位置。
            KedgeSmallTopBar(
                title = stringResource(R.string.about_page_title),
                navigationIcon = {
                    BackButton()
                },
                titleAlpha = titleAlpha,
                barColorAlpha = animatedProgress,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        EmojiBurstHost(
                modifier = Modifier.fillMaxSize(),
                emojiOptions = emojiOptions,
                burstCount = 12
            ) { onBurst ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding + PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "about_hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                AsyncImage(
                                    model = R.mipmap.ic_launcher,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(CircleShape)
                                        .graphicsLayer {
                                            translationY = -logoProgress * 56.dp.toPx()
                                            val shrink = 1f - logoProgress * 0.05f
                                            scaleX = shrink
                                            scaleY = shrink
                                            alpha = 1f - logoProgress
                                        }
                                        .onGloballyPositioned { coordinates ->
                                            val position = coordinates.positionInParent()
                                            val size = coordinates.size
                                            logoCenterPx = Offset(
                                                position.x + size.width / 2f,
                                                position.y + size.height / 2f
                                            )
                                        }
                                        .pointerInput(soundEffectPlayer) {
                                            detectTapGestures {
                                                onBurst(logoCenterPx)
                                                soundEffectPlayer.play(soundOptions.random())
                                            }
                                        },
                                )
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .graphicsLayer {
                                            translationY = -nameProgress * 36.dp.toPx()
                                            val shrink = 1f - nameProgress * 0.05f
                                            scaleX = shrink
                                            scaleY = shrink
                                            alpha = 1f - nameProgress
                                        },
                                )
                                Text(
                                    text = stringResource(
                                        R.string.about_page_version_format,
                                        BuildConfig.VERSION_NAME,
                                        BuildConfig.VERSION_CODE,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .graphicsLayer {
                                            translationY = -versionProgress * 20.dp.toPx()
                                            val shrink = 1f - versionProgress * 0.05f
                                            scaleX = shrink
                                            scaleY = shrink
                                            alpha = 1f - versionProgress
                                        },
                                )
                            }
                        }
                    }

                    item(key = "about_links") {
                        CardGroup(
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            item(
                                onClick = { context.openUrl("${websiteUrl}/") },
                                leadingContent = { Icon(public, null) },
                                supportingContent = { Text(websiteUrl) },
                                headlineContent = { Text(stringResource(R.string.about_page_website)) },
                            )
                            item(
                                onClick = { context.openUrl(githubUrl) },
                                leadingContent = { Icon(code, null) },
                                supportingContent = { Text(githubUrl) },
                                headlineContent = { Text(stringResource(R.string.about_page_github)) },
                            )
                            item(
                                onClick = { context.openUrl(licenseUrl) },
                                leadingContent = { Icon(insertDriveFile, null) },
                                supportingContent = {
                                    Text(licenseUrl)
                                },
                                headlineContent = { Text(stringResource(R.string.about_page_license)) },
                            )
                        }
                    }

                    // 列表末尾留白，保证内容再少也能滑够、头图能完整收起。
                    // 没有它时本页内容不足一屏，滚动偏移恒为 0，顶栏与流光
                    // 永远不会淡入。与 Miuix 版同源（lyricon AboutScreen.kt）。
                    item(key = "about_bottom_space") {
                        Spacer(
                            Modifier
                                .fillParentMaxHeight(0.72f)
                                .heightIn(min = 160.dp)
                                .navigationBarsPadding()
                        )
                    }
                }
            }
    }
    }
}
