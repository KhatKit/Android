package heizige.kk.khatkit.app.feature.onboarding

import heizige.kk.khatkit.app.LocalPageRoute
import heizige.kk.khatkit.uikit.mochi.KkGroupedColumn
import ui.feice.mochi.list.MochiListDefaults
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.Md3Checkbox
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import ui.feice.mochi.theme.MochiTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlinx.coroutines.delay
import ui.feice.mochi.basic.MochiButton as MochiRealButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiPageControl
import ui.feice.mochi.basic.Text as MochiText
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.mochiBlur
import ui.feice.mochi.effect.content.mochiBlurAlpha
import ui.feice.mochi.effect.content.mochiBlurReveal
import ui.feice.mochi.effect.mask.mochiScrollFadeMask
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiTitleBarDefaults
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.emphasized

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.ui.components.permission.PermissionChecklist
import heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode
import heizige.kk.khatkit.app.feature.settings.components.PresetThemeColorDots
import heizige.kk.khatkit.app.feature.settings.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle

import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.uikit.mochi.KkOptionsText
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.MochiModalBottomSheet
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.neurology
import heizige.kk.khatkit.app.core.ui.icons.smartphone
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

import heizige.kk.khatkit.uikit.mochi.KkSingleChoiceSegmentedRow
import heizige.kk.khatkit.uikit.mochi.SegmentedItem
import androidx.compose.runtime.DisposableEffect
import heizige.kk.khatkit.app.PageBackInterceptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import heizige.kk.khatkit.uikit.mochi.rememberKkHaptics
import ui.feice.mochi.basic.MochiSelectFrame
import ui.feice.mochi.basic.MochiSelectionCheck
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection

private enum class GreetingStep { Welcome, Agreement, Permissions, AiSetup, Settings }

private const val KEY_GREETING_COMPLETED = "greeting_completed"

/**
 * 首次启动引导：欢迎 → 用户协议 → 权限 → AI 服务 → 初始化设置。
 *
 * 照 Mochi 示例的引导（`DemoOnboarding`）：分组底色整页，顶栏只有一个按需出现的返回键；
 * 新页从右边整页推进来、带一点模糊，旧页往左挪一小段、缩小、糊掉、淡出；
 * 每页标题、副标题依次糊着浮上来；底部页码点 + 一颗通栏大按钮。完成后由 [onFinish] 写入
 * [KEY_GREETING_COMPLETED]。返回仍走 [PageBackInterceptor]（全 app 只有 RouteActivity 接返回）。
 */
@Composable
fun GreetingPage(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(GreetingStep.Welcome) }
    var agreementAccepted by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val pageCount = GreetingStep.entries.size
    val pager = remember { Animatable(step.ordinal.toFloat()) }
    val staggers = remember { List(pageCount) { Animatable(0f) } }
    LaunchedEffect(step) { pager.animateTo(step.ordinal.toFloat(), spring(dampingRatio = 0.92f, stiffness = 210f)) }
    // 刚启动时欢迎页稍等一下再开始：先让首帧的排版过去，标题再出场
    var firstWelcome by remember { mutableStateOf(true) }
    LaunchedEffect(step) {
        val stagger = staggers[step.ordinal]
        stagger.snapTo(0f)
        if (step == GreetingStep.Welcome && firstWelcome) delay(WelcomeLeadMillis)
        if (step != GreetingStep.Welcome) firstWelcome = false
        stagger.animateTo(1f, tween(StaggerMillis.toInt(), easing = LinearEasing))
    }

    val goBack: () -> Unit = {
        if (step != GreetingStep.Welcome) step = GreetingStep.entries[step.ordinal - 1]
    }
    // 协议没同意前不能越过协议页（底部按钮和页码点都受这条约束）
    val canEnter: (GreetingStep) -> Boolean = { target ->
        target.ordinal <= GreetingStep.Agreement.ordinal || agreementAccepted
    }

    // U7a-6：页面级返回收敛到 PageBackInterceptor（返回单点仍是 Mochi 的 PredictiveBackHandler）。
    // 只在引导流程可回退的步骤登记；Welcome 步不登记，否则会常驻抢走系统返回、磨掉预测动画。
    // 登记时带上所在页：只拦本页的返回，压在下面的页里还开着的浮层不会吞掉栈顶页的返回
    val stepBackTokenRoute = LocalPageRoute.current
    val stepBackToken = remember { Any() }
    val stepBackEnabled = step != GreetingStep.Welcome
    DisposableEffect(stepBackEnabled) {
        if (stepBackEnabled) {
            PageBackInterceptor.push(stepBackToken, stepBackTokenRoute) {
                goBack()
                true
            }
        } else {
            PageBackInterceptor.pop(stepBackToken)
        }
        onDispose { PageBackInterceptor.pop(stepBackToken) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MochiDefaults.groupedBackground())
            .systemBarsPadding(),
    ) {
        // 和 MochiTitleBar 同一套顶栏尺寸，返回键和其它页面的顶栏按钮对齐
        Row(
            Modifier
                .fillMaxWidth()
                .height(MochiTitleBarDefaults.Height)
                .padding(horizontal = MochiButtonDefaults.BarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GreetingTopIcon(visible = step != GreetingStep.Welcome) {
                MochiBackButton(onClick = goBack)
            }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val width = constraints.maxWidth.toFloat()
            GreetingStep.entries.forEach { page ->
                val i = page.ordinal
                key(page) {
                    // 前后两页提前组合好（下一页在屏幕右边外面、上一页透明度 0）：第一次翻到某页时不在翻页那一帧现组合
                    val present by remember { derivedStateOf { abs(i - pager.value) < 0.999f || abs(i - step.ordinal) == 1 } }
                    if (present) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val d = i - pager.value
                                    if (d >= 0f) {
                                        translationX = d * width
                                    } else {
                                        translationX = d * width * 0.28f
                                        val s = 1f + d * 0.06f
                                        scaleX = s
                                        scaleY = s
                                        mochiBlurAlpha((1f + d).coerceIn(0f, 1f), 10.dp * -d, continuous = true)
                                    }
                                }
                                .mochiBlur(continuous = true) {
                                    val d = i - pager.value
                                    if (d >= 0f) 12.dp * d else 10.dp * -d
                                }
                                .graphicsLayer {
                                    val d = i - pager.value
                                    val moving = abs(d) > 0.001f
                                    clip = moving
                                    if (moving) shape = MochiRoundedRectangle(PageCorner * (abs(d) * 6f).coerceAtMost(1f))
                                },
                        ) {
                            val t = { staggers[i].value }
                            when (page) {
                                GreetingStep.Welcome -> GreetingWelcomeScreen(t)
                                GreetingStep.Agreement -> GreetingAgreementScreen(
                                    t = t,
                                    scrollState = scrollState,
                                    accepted = agreementAccepted,
                                    onAcceptedChange = { agreementAccepted = it },
                                )

                                GreetingStep.Permissions -> GreetingPermissionsScreen(t)
                                GreetingStep.AiSetup -> GreetingAiSetupScreen(t)
                                GreetingStep.Settings -> GreetingSettingsScreen(t)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            MochiPageControl(
                pageCount = pageCount,
                position = { pager.value },
                onPageSelect = { target ->
                    val page = GreetingStep.entries[target]
                    if (canEnter(page)) step = page
                },
                modifier = Modifier.mochiBlurReveal({ pager.value.coerceIn(0f, 1f) }, blur = 6.dp),
            )
            if (step == GreetingStep.Welcome) {
                // 欢迎页上页码点是隐藏的，盖一层接住点击
                Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
            }
        }
        Spacer(Modifier.height(12.dp))
        val isReadAction = step == GreetingStep.Agreement && scrollState.canScrollForward
        MochiRealButton(
            onClick = {
                when {
                    step == GreetingStep.Settings -> onFinish()
                    isReadAction -> scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                    else -> step = GreetingStep.entries[step.ordinal + 1]
                }
            },
            enabled = step != GreetingStep.Agreement || isReadAction || agreementAccepted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            minHeight = 54.dp,
        ) {
            MochiBlurText(
                when {
                    step == GreetingStep.Settings -> stringResource(R.string.greeting_action_finish)
                    isReadAction -> stringResource(R.string.greeting_action_read)
                    else -> stringResource(R.string.greeting_action_next)
                },
                style = MochiTheme.textStyles.bodyLarge.emphasized(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

private val PageCorner = 32.dp
private const val StaggerMillis = 1300f
private const val WelcomeLeadMillis = 500L

@Composable
private fun GreetingTopIcon(visible: Boolean, content: @Composable () -> Unit) {
    // 出现 / 消失：模糊淡入淡出 + 轻微缩放；看不见时不接收点击
    val a by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "top")
    Box(
        Modifier
            .graphicsLayer {
                val s = 0.8f + 0.2f * a
                scaleX = s
                scaleY = s
            }
            .mochiBlurReveal({ a }, blur = 6.dp),
    ) {
        content()
        if (!visible) Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
    }
}

/** 页内元素按顺序依次升起：第 [i] 个比前一个晚 70ms，各自 480ms 从下面糊着浮上来 */
private fun Modifier.rise(t: () -> Float, i: Int): Modifier = mochiBlurReveal({
    val v = ((t() * StaggerMillis - i * 70f) / 480f).coerceIn(0f, 1f)
    1f - (1f - v) * (1f - v) * (1f - v)
}, blur = 10.dp, rise = 22.dp)

/** 卡片类元素弹出来：和 [rise] 同样按顺序浮上来，同时从 0.82 带回弹放大到 1 */
private fun Modifier.pop(t: () -> Float, i: Int): Modifier = graphicsLayer {
    val ms = t() * StaggerMillis - i * 70f
    val k = if (ms <= 0f) 0f else 1f - exp(-ms / 120f) * cos(ms / 75f)
    val sc = 0.82f + 0.18f * k
    scaleX = sc
    scaleY = sc
}.mochiBlurReveal({
    val v = ((t() * StaggerMillis - i * 70f) / 360f).coerceIn(0f, 1f)
    1f - (1f - v) * (1f - v) * (1f - v)
}, blur = 10.dp, rise = 18.dp)

/** 每页顶部的大标题 + 说明，跟着页一起滚 */
@Composable
private fun GreetingHeading(title: String, subtitle: String?, t: () -> Float) {
    val ts = MochiTheme.textStyles
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MochiText(title, style = ts.largeTitle.copy(textAlign = TextAlign.Center), modifier = Modifier.rise(t, 0))
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            MochiBlurText(
                subtitle,
                style = ts.bodyLarge.copy(textAlign = TextAlign.Center),
                color = MochiTheme.colorScheme.onSurfaceVariant,
                contentAlignment = Alignment.Center,
                modifier = Modifier.rise(t, 1),
            )
        }
    }
}

@Composable
private fun GreetingHeading(step: GreetingStep, t: () -> Float) {
    val subtitle = when (step) {
        GreetingStep.Agreement -> stringResource(R.string.greeting_agreement_subtitle)
        GreetingStep.Permissions -> stringResource(R.string.greeting_permissions_subtitle)
        GreetingStep.AiSetup -> stringResource(R.string.greeting_ai_subtitle)
        GreetingStep.Settings -> stringResource(R.string.greeting_settings_subtitle)
        GreetingStep.Welcome -> null
    }
    val title = when (step) {
        GreetingStep.Agreement -> stringResource(R.string.greeting_title_agreement)
        GreetingStep.Permissions -> stringResource(R.string.greeting_title_permissions)
        GreetingStep.AiSetup -> stringResource(R.string.greeting_title_ai)
        GreetingStep.Settings -> stringResource(R.string.greeting_title_settings)
        GreetingStep.Welcome -> ""
    }
    GreetingHeading(title, subtitle, t)
}

/** 页内滚动列：还能往哪边滚，那一边渐隐 */
@Composable
private fun GreetingPageColumn(
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .mochiScrollFadeMask(scrollState)
            .verticalScroll(scrollState, flingBehavior = MochiScrollDefaults.flingBehavior())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
private fun GreetingWelcomeScreen(t: () -> Float) {
    val ts = MochiTheme.textStyles
    val scroll = rememberScrollState()
    // 内容居中：不长也能上下拖出越界回弹
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, flingBehavior = MochiScrollDefaults.flingBehavior())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .pop(t, 0)
                    .size(112.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(28.dp))
            MochiText(
                stringResource(R.string.greeting_welcome_title, stringResource(R.string.app_name)),
                style = ts.titleLarge.emphasized(),
                modifier = Modifier.rise(t, 2),
            )
            Spacer(Modifier.height(8.dp))
            MochiText(
                stringResource(R.string.greeting_welcome_desc),
                style = ts.bodyLarge.copy(textAlign = TextAlign.Center),
                color = MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rise(t, 3),
            )
        }
    }
}

@Composable
private fun GreetingAgreementScreen(
    t: () -> Float,
    scrollState: androidx.compose.foundation.ScrollState,
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
) {
    val cards = listCardStyle()
    GreetingPageColumn(scrollState) {
        GreetingHeading(GreetingStep.Agreement, t)
        MochiText(
            text = stringResource(R.string.greeting_agreement_text),
            style = MochiTheme.textStyles.body,
            color = MochiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .rise(t, 2),
        )
        Spacer(modifier = Modifier.height(20.dp))
        MochiOptionItem(
            onClick = { onAcceptedChange(!accepted) },
            modifier = Modifier.rise(t, 3),
            shape = cards.groupItemShape(isFirst = true, isLast = true),
            leadingContent = {
                Md3Checkbox(
                    checked = accepted,
                    onCheckedChange = null,
                )
            },
            titleContent = { Text(stringResource(R.string.greeting_agreement_accept)) },
        )
    }
}

// ---------- 初始化设置：外观（照 Mochi 引导的「外观」页） ----------

private enum class Shade(val mode: ColorMode) { Light(ColorMode.LIGHT), Dark(ColorMode.DARK), Auto(ColorMode.SYSTEM) }

@Composable
private fun Shade.label(): String = when (this) {
    Shade.Light -> stringResource(R.string.greeting_settings_theme_light)
    Shade.Dark -> stringResource(R.string.greeting_settings_theme_dark)
    Shade.Auto -> stringResource(R.string.greeting_settings_theme_system)
}

@Composable
private fun GreetingSettingsScreen(t: () -> Float) {
    val settingsStore: SettingsRepository = rememberAppEntryPoint().settingsStore()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val colorMode = rememberColorMode()
    val changeColorMode = heizige.kk.khatkit.app.core.ui.theme.rememberColorModeChanger(colorMode)
    val haptics = rememberKkHaptics()
    val scope = rememberCoroutineScope()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val monet = dynamicColorSupported && settings.dynamicColor

    // 色板回显：跟随壁纸时不选中任何色块；预设主题取主色，自定义主题取保存的种子色
    val selectedSeedColor: Color? = if (monet) {
        null
    } else {
        PresetThemes.firstOrNull { it.id == settings.themeId }?.standardLight?.primary
            ?: settings.customThemes.firstOrNull { it.id == settings.themeId }
                ?.let { Color(it.primaryColorArgb.toInt()) }
    }

    // 色板选到预设之外的颜色：复用同种子的自定义主题，没有就建一个并选中
    fun applyCustomSeed(color: Color) {
        val argb = color.toArgb().toLong() and 0xFFFFFFFFL
        scope.launch {
            settingsStore.update { current ->
                val existing = current.customThemes.firstOrNull { it.primaryColorArgb == argb }
                if (existing != null) {
                    current.copy(dynamicColor = false, themeId = existing.id)
                } else {
                    val custom = CustomTheme(
                        name = String.format("#%08X", color.toArgb()),
                        primaryColorArgb = argb,
                    )
                    current.copy(
                        dynamicColor = false,
                        customThemes = current.customThemes + custom,
                        themeId = custom.id,
                        customColorHistory = (
                            listOf(argb) +
                                current.customColorHistory.filterNot { it == argb }
                            ).take(8),
                    )
                }
            }
        }
    }

    GreetingPageColumn {
        GreetingHeading(GreetingStep.Settings, t)
        val shade = Shade.entries.first { it.mode == colorMode.value }
        Row(
            Modifier
                .fillMaxWidth()
                .pop(t, 2),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Shade.entries.forEach { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MochiSelectFrame(
                        s == shade,
                        cornerRadius = 20.dp,
                        modifier = Modifier
                            .width(84.dp)
                            .aspectRatio(0.56f),
                        onClick = {
                            if (s != shade) {
                                haptics.tick()
                                // 深浅真的变了才从手指处揭开（见 rememberColorModeChanger）
                                changeColorMode(s.mode)
                            }
                        },
                    ) {
                        ShadeThumb(s, MochiTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    MochiText(s.label(), style = MochiTheme.textStyles.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    MochiSelectionCheck(s == shade)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .pop(t, 3),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            // 换种子色是连续的色相变化：走配色插值，不揭开
            if (dynamicColorSupported) {
                MochiListSection {
                    MochiListRow(
                        stringResource(R.string.greeting_settings_dynamic_color),
                        subtitle = stringResource(R.string.greeting_settings_dynamic_color_desc),
                        trailing = {
                            MochiSwitch(monet, { enabled ->
                                scope.launch { settingsStore.update { it.copy(dynamicColor = enabled) } }
                            })
                        },
                    )
                }
            }
            MochiListSection(header = stringResource(R.string.greeting_settings_theme_color)) {
                PresetThemeColorDots(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    selectedThemeId = settings.themeId,
                    onSelectTheme = { themeId ->
                        scope.launch { settingsStore.update { it.copy(dynamicColor = false, themeId = themeId) } }
                    },
                    onCustomColor = { applyCustomSeed(it) },
                    selectedSeedColor = selectedSeedColor,
                )
            }
        }
    }
}

/** 明暗缩略图：一块小屏，上面两张卡片和几条字条；自动 = 斜着一半浅一半深 */
@Composable
private fun ShadeThumb(shade: Shade, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        fun screen(dark: Boolean) {
            val bg = if (dark) Color(0xFF111111) else Color(0xFFF2F2F7)
            val card = if (dark) Color(0xFF2A2A2C) else Color.White
            val bar = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.12f)
            drawRect(bg)
            val pad = size.width * 0.12f
            val w = size.width - pad * 2f
            val r = CornerRadius(size.width * 0.08f)
            drawRoundRect(bar, Offset(pad, size.height * 0.1f), Size(w * 0.55f, size.height * 0.035f), r)
            drawRoundRect(card, Offset(pad, size.height * 0.2f), Size(w, size.height * 0.28f), r)
            for (k in 0 until 3) {
                val y = size.height * (0.24f + k * 0.08f)
                drawCircle(if (k == 0) accent else bar, size.width * 0.045f, Offset(pad + w * 0.14f, y + size.height * 0.015f))
                drawRoundRect(bar, Offset(pad + w * 0.28f, y), Size(w * 0.5f, size.height * 0.03f), r)
            }
            drawRoundRect(card, Offset(pad, size.height * 0.53f), Size(w, size.height * 0.16f), r)
            drawRoundRect(accent, Offset(pad, size.height * 0.82f), Size(w, size.height * 0.08f), CornerRadius(size.height))
        }
        when (shade) {
            Shade.Light -> screen(false)

            Shade.Dark -> screen(true)

            Shade.Auto -> {
                screen(false)
                val cut = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                clipPath(cut) { screen(true) }
            }
        }
    }
}

@Composable
private fun GreetingPermissionsScreen(t: () -> Float) {
    PermissionChecklist(header = { GreetingHeading(GreetingStep.Permissions, t) })
}

@Composable
private fun GreetingAiSetupScreen(t: () -> Float) {
    val settingsStore: SettingsRepository = rememberAppEntryPoint().settingsStore()
    val providerManager: ProviderManager = rememberAppEntryPoint().providerManager()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val cards = listCardStyle()
    val providers = settings.providers.filter { it.builtIn }
    val configuredProviders = providers.filter { it.apiKeyOrEmpty().isNotBlank() }
    val unconfiguredProviders = providers.filter { it.apiKeyOrEmpty().isBlank() }
    var sheetProvider by remember { mutableStateOf<ProviderSetting?>(null) }
    var draftKey by remember { mutableStateOf("") }
    var draftBaseUrl by remember { mutableStateOf("") }
    var draftName by remember { mutableStateOf("") }
    var customType by remember { mutableStateOf<KClass<out ProviderSetting>>(ProviderSetting.OpenAI::class) }
    var customSheetVisible by remember { mutableStateOf(false) }

    suspend fun bindDefaultModels(providerId: Uuid) {
        val current = settingsStore.settingsFlow.value
        val provider = current.providers.find { it.id == providerId } ?: return
        val models = runCatching {
            providerManager.getProviderByType(provider).listModels(provider)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return

        settingsStore.update { latest ->
            val target = latest.providers.find { it.id == providerId } ?: return@update latest
            val existingIds = target.models.map { it.modelId }.toSet()
            val merged = models
                .filter { it.modelId !in existingIds }
                .fold(target) { acc, model -> acc.addModel(model) }
            val defaultModel = models.firstOrNull { it.type == ModelType.CHAT } ?: models.first()
            latest.copy(
                providers = latest.providers.map { if (it.id == providerId) merged else it },
                chatModelId = if (latest.findModelById(latest.chatModelId) != null) {
                    latest.chatModelId
                } else {
                    defaultModel.id
                },
                fastModelId = if (latest.findModelById(latest.fastModelId) != null) {
                    latest.fastModelId
                } else {
                    defaultModel.id
                },
            )
        }
    }

    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .mochiScrollFadeMask(listState),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(cards.gap),
        flingBehavior = MochiScrollDefaults.flingBehavior(),
    ) {
        item(key = "heading") {
            GreetingHeading(GreetingStep.AiSetup, t)
        }
        item(key = "custom_header") {
            KkOptionsText(stringResource(R.string.greeting_ai_enabled_models))
        }
        items(configuredProviders, key = { it.id }) { provider ->
            val index = configuredProviders.indexOf(provider)
            KkGroupedColumn(
                modifier = Modifier.animateItem(),
                shape = cards.indexedShape(index, configuredProviders.size + 1),
                containerColor = MochiListDefaults.colors().container,
                topDivider = index > 0 && cards.gap == 0.dp,
            ) {
                AiProviderItem(
                    provider = provider,
                    configured = true,
                    shape = cards.indexedShape(index, configuredProviders.size + 1),
                    onClick = {
                        draftKey = provider.apiKeyOrEmpty()
                        draftBaseUrl = provider.baseUrlOrEmpty()
                        sheetProvider = provider
                    },
                    onToggle = { enabled ->
                        scope.launch {
                            settingsStore.update { current ->
                                current.copy(
                                    providers = current.providers.map {
                                        if (it.id == provider.id) it.withEnabled(enabled) else it
                                    }
                                )
                            }
                        }
                    },
                )
            }
        }

        item(key = "custom_button") {
            KkGroupedColumn(
                shape = cards.indexedShape(configuredProviders.size, configuredProviders.size + 1),
                containerColor = MochiListDefaults.colors().container,
                topDivider = configuredProviders.size > 0 && cards.gap == 0.dp,
            ) {
                MochiOptionItem(
                    shape = cards.indexedShape(
                        configuredProviders.size,
                        configuredProviders.size + 1,
                    ),
                    onClick = {
                        sheetProvider = null
                        customType = ProviderSetting.OpenAI::class
                        draftName = ""
                        draftKey = ""
                        draftBaseUrl = defaultBaseUrlFor(ProviderSetting.OpenAI::class)
                        customSheetVisible = true
                    },
                    leadingContent = {
                        Icon(
                            imageVector = add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.87f),
                        )
                    },
                    titleContent = { Text(stringResource(R.string.greeting_ai_custom_key)) },
                    supportingContent = { Text(stringResource(R.string.greeting_ai_custom_desc)) },
                )
            }
        }

        item(key = "providers_header") {
            Spacer(modifier = Modifier.height(10.dp))
            KkOptionsText(stringResource(R.string.greeting_ai_providers))
        }
        items(unconfiguredProviders, key = { it.id }) { provider ->
            val index = unconfiguredProviders.indexOf(provider)
            KkGroupedColumn(
                modifier = Modifier.animateItem(),
                shape = cards.indexedShape(index, unconfiguredProviders.size),
                containerColor = MochiListDefaults.colors().container,
                topDivider = index > 0 && cards.gap == 0.dp,
            ) {
                AiProviderItem(
                    provider = provider,
                    configured = false,
                    shape = cards.indexedShape(index, unconfiguredProviders.size),
                    onClick = {
                        draftKey = provider.apiKeyOrEmpty()
                        draftBaseUrl = provider.baseUrlOrEmpty()
                        sheetProvider = provider
                    },
                    onToggle = {},
                )
            }
        }

    }

    if (sheetProvider != null || sheetProvider == null && customSheetVisible) {
        val provider = sheetProvider
        val save: () -> Unit = {
            scope.launch {
                    val existingId = provider?.id
                    var createdId: Uuid? = null
                    settingsStore.update { current ->
                        if (provider != null) {
                            current.copy(
                                providers = current.providers.map {
                                    if (it.id == provider.id) {
                                        it.withConfig(draftKey.trim(), draftBaseUrl.trim()).withEnabled(true)
                                    } else {
                                        it
                                    }
                                }
                            )
                        } else {
                            val created = createCustomProvider(
                                type = customType,
                                name = draftName,
                                baseUrl = draftBaseUrl,
                                apiKey = draftKey,
                            )
                            createdId = created.id
                            current.copy(providers = current.providers + created)
                        }
                    }
                    (existingId ?: createdId)?.let { bindDefaultModels(it) }
            }
            sheetProvider = null
            customSheetVisible = false
        }
        val sheetTitle = provider?.name ?: stringResource(R.string.greeting_ai_custom_key)
        val closeSheet = { sheetProvider = null; customSheetVisible = false }
        val sheetBody: @Composable (showSaveButton: Boolean) -> Unit = { showSaveButton ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (provider == null) {
                    KkSingleChoiceSegmentedRow(
                        modifier = Modifier.fillMaxWidth(),
                        items = ProviderSetting.Types.map { type ->
                            SegmentedItem(
                                label = type.simpleName ?: "",
                                selected = customType == type,
                                onClick = {
                                    customType = type
                                    if (draftBaseUrl.isBlank()) {
                                        draftBaseUrl = defaultBaseUrlFor(type)
                                    }
                                },
                            )
                        },
                    )
                    Md3OutlinedTextFieldWithSlots(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text(stringResource(R.string.greeting_ai_custom_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                Md3OutlinedTextFieldWithSlots(
                    value = draftKey,
                    onValueChange = { draftKey = it },
                    label = { Text(stringResource(R.string.greeting_ai_api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Md3OutlinedTextFieldWithSlots(
                    value = draftBaseUrl,
                    onValueChange = { draftBaseUrl = it },
                    label = { Text(stringResource(R.string.greeting_ai_base_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                if (showSaveButton) {
                    MochiButton(
                        onClick = save,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.greeting_ai_save))
                    }
                }
            }
        }
        if (false) {
            MochiModalBottomSheet(
                show = true,
                title = sheetTitle,
                onDismissRequest = closeSheet,
                // 本处的 MD3 分支（下面的 PrimaryBottomSheet）走的是 Khromia
                // PrimaryBottomSheet，其内容容器横向零内缩；Miuix 的
                // WindowBottomSheet 缺省会缩 16dp。这里显式归零，两侧贴边就都等于
                // sheetBody 自己的 16dp（0+16 = 0+16），不会因切风格而跳一下。
                miuixInsideMarginWidth = 0.dp,
            ) { sheetBody(true) }
        } else {
            PrimaryBottomSheet(
                visible = true,
                title = sheetTitle,
                imageVector = smartphone,
                confirmText = stringResource(R.string.greeting_ai_save),
                onConfirm = { save() },
                onDismiss = closeSheet,
                scrollable = false,
            ) { _ -> sheetBody(false) }
        }
    }
}

@Composable
private fun AiProviderItem(
    provider: ProviderSetting,
    configured: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    MochiOptionItem(
        onClick = onClick,
        shape = shape,
        modifier = modifier,
        leadingContent = {
            Icon(
                imageVector = neurology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.87f),
            )
        },
        titleContent = { Text(provider.name) },
        supportingContent = {
            Text(
                stringResource(
                    if (configured) R.string.greeting_ai_configured else R.string.greeting_ai_not_configured
                )
            )
        },
        trailingContent = {
            KkSwitch(
                checked = configured && provider.enabled,
                enabled = configured,
                onCheckedChange = onToggle,
            )
        },
    )
}

private fun ProviderSetting.apiKeyOrEmpty(): String = when (this) {
    is ProviderSetting.OpenAI -> apiKey
    is ProviderSetting.Google -> apiKey
    is ProviderSetting.Claude -> apiKey
    is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> ""
    else -> ""
}

private fun ProviderSetting.baseUrlOrEmpty(): String = when (this) {
    is ProviderSetting.OpenAI -> baseUrl
    is ProviderSetting.Google -> baseUrl
    is ProviderSetting.Claude -> baseUrl
    is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> ""
    else -> ""
}

private fun ProviderSetting.withEnabled(enabled: Boolean): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(enabled = enabled)
    is ProviderSetting.Google -> copy(enabled = enabled)
    is ProviderSetting.Claude -> copy(enabled = enabled)
    is ProviderSetting.ChatGPT -> copy(enabled = enabled)
    is ProviderSetting.Grok -> copy(enabled = enabled)
    else -> this
}

private fun ProviderSetting.withConfig(apiKey: String, baseUrl: String): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(apiKey = apiKey, baseUrl = baseUrl.ifBlank { this.baseUrl }, enabled = true)
    is ProviderSetting.Google -> copy(apiKey = apiKey, baseUrl = baseUrl.ifBlank { this.baseUrl }, enabled = true)
    is ProviderSetting.Claude -> copy(apiKey = apiKey, baseUrl = baseUrl.ifBlank { this.baseUrl }, enabled = true)
    // 账号类提供商不配置 apiKey/baseUrl，只打开 enabled；分别 smart-cast 到具体类型才有 copy
    is ProviderSetting.ChatGPT -> copy(enabled = true)
    is ProviderSetting.Grok -> copy(enabled = true)
}

private fun defaultBaseUrlFor(type: KClass<out ProviderSetting>): String = when (type) {
    ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
    ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
    else -> ProviderSetting.OpenAI().baseUrl
}

private fun createCustomProvider(
    type: KClass<out ProviderSetting>,
    name: String,
    baseUrl: String,
    apiKey: String,
): ProviderSetting {
    val resolvedName = name.trim().ifBlank { type.simpleName ?: "Custom" }
    val resolvedBaseUrl = baseUrl.trim().ifBlank { defaultBaseUrlFor(type) }
    val resolvedKey = apiKey.trim()
    return when (type) {
        ProviderSetting.Google::class -> ProviderSetting.Google(
            name = resolvedName,
            baseUrl = resolvedBaseUrl,
            apiKey = resolvedKey,
            enabled = true,
            builtIn = false,
        )

        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            name = resolvedName,
            baseUrl = resolvedBaseUrl,
            apiKey = resolvedKey,
            enabled = true,
            builtIn = false,
        )

        else -> ProviderSetting.OpenAI(
            name = resolvedName,
            baseUrl = resolvedBaseUrl,
            apiKey = resolvedKey,
            enabled = true,
            builtIn = false,
        )
    }
}
