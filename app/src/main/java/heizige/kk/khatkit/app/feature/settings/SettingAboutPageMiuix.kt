package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.effect.BgEffectBackground
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.core.ui.icons.public
import heizige.kk.khatkit.app.core.util.SoundEffectPlayer
import heizige.kk.khatkit.app.core.util.openUrl
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 关于页（Miuix）。
 *
 * 布局照搬 KernelSU `ui/screen/about/AboutMiuix.kt`：顶部是一块大 logo 头图，滚动时按
 * 「版本号 → 应用名 → logo」的顺序分三段淡出、缩小并上移，头图收起后交给下面的分组卡片。
 * 顶栏、毛玻璃、滚动行为全部复用 [MiuixSettingsPage]，本页不重复搭 Scaffold。
 *
 * 与 KernelSU 原版的两点差异：
 * - KSU 用 `onGloballyPositioned` 量出各元素绝对 Y 再按位移分段算进度；这里改成用头图
 *   滚动进度映射到三段区间。视觉一致，但不会因字体缩放或元素换位算错分段边界。
 * - KSU 给文字叠了 `textureBlur(DstIn)` 做「化掉」的过渡；本头图已处在
 *   [MiuixSettingsPage] 的模糊层内部，再叠一层纹理模糊会重复采样，故只用透明度+缩放+位移。
 */
@Composable
fun SettingAboutPageMiuix() {
    val context = LocalContext.current
    val soundOptions = remember { listOf(R.raw.bingbingbing, R.raw.gangguan) }
    val soundEffectPlayer = remember(context) { SoundEffectPlayer(context) }
    DisposableEffect(soundEffectPlayer) {
        soundEffectPlayer.preload(*soundOptions.toIntArray())
        onDispose { soundEffectPlayer.release() }
    }

    val websiteUrl = stringResource(R.string.about_page_url_website)
    val githubUrl = stringResource(R.string.about_page_url_github)
    val licenseUrl = stringResource(R.string.about_page_url_license)

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
    // 顶栏标题延后淡入：起始全透明（只剩返回按钮），滚过 35% 后才开始显现。
    // 与 KernelSU AboutMiuix.kt 的 titleColor 曲线一致。
    val titleAlpha = ((animatedProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)

    // 流光背景铺满整屏，滚过头图后随进度淡出
    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        alpha = { 1f - animatedProgress },
    ) {
        MiuixSettingsPage(
            title = stringResource(R.string.about_page_title),
            navigationIcon = { BackButton() },
            lazyListState = listState,
            // 让底层流光背景透出来（默认 surface 不透明会盖住 Canvas）
            containerColor = Color.Transparent,
            titleAlpha = titleAlpha,
            smallTitleBar = true,
            // 顶栏底色随滚动淡入；非 null 时不套毛玻璃（底层就是流光）
            barColorAlpha = animatedProgress,
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
                            .pointerInput(soundEffectPlayer) {
                                detectTapGestures { soundEffectPlayer.play(soundOptions.random()) }
                            },
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        color = colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
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
                        color = colorScheme.onBackground,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
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

        miuixGroup(key = "about_links") {
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
        }
    }
}
