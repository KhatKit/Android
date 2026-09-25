package heizige.kk.khatkit.app.ui.pages.greeting

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.datastore.SettingsStore
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.ui.components.permission.PermissionChecklist
import heizige.kk.khatkit.app.core.ui.hooks.rememberColorMode
import heizige.kk.khatkit.app.feature.settings.components.MorphThemeModeSelector
import heizige.kk.khatkit.app.feature.settings.components.PresetThemeColorDots
import heizige.kk.khatkit.app.feature.settings.components.ThemeCustomColorSheet
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.CustomTheme
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.feature.automation.KhatKitAccessibilityService
import heizige.kk.khatkit.app.core.util.hasUsageStatsPermission
import heizige.kk.khatkit.bridge.impl.AllFilesAccess
import heizige.kk.khatkit.bridge.impl.RootBridgeImpl
import heizige.kk.khatkit.bridge.impl.ShizukuPermission
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import heizige.kk.khromia.components.AnimatedRadioItem
import heizige.kk.khromia.components.ButtonOption
import heizige.kk.khromia.components.ExpandableOptionItem
import heizige.kk.khromia.components.OptionItem
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khromia.helper.fadingEdge
import heizige.kk.khromia.text.OptionsText
import heizige.kk.kedge.adaptive.KedgeTopAppBar
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeCheckbox
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeRadioButton
import heizige.kk.kedge.components.KedgeStatusDefaults
import heizige.kk.kedge.components.KedgeStatusLevel
import heizige.kk.kedge.components.KedgeStatusTag
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.components.KedgeWarningCard
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeModalBottomSheet
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.accessibility
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.barChart
import heizige.kk.khatkit.app.core.ui.icons.bluetooth
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.brightnessAuto
import heizige.kk.khatkit.app.core.ui.icons.calendarMonth
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.checkCircle
import heizige.kk.khatkit.app.core.ui.icons.darkMode
import heizige.kk.khatkit.app.core.ui.icons.folderOpen
import heizige.kk.khatkit.app.core.ui.icons.lightMode
import heizige.kk.khatkit.app.core.ui.icons.locationOn
import heizige.kk.khatkit.app.core.ui.icons.menuBook
import heizige.kk.khatkit.app.core.ui.icons.mic
import heizige.kk.khatkit.app.core.ui.icons.neurology
import heizige.kk.khatkit.app.core.ui.icons.notifications
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.icons.photoCamera
import heizige.kk.khatkit.app.core.ui.icons.pictureInPicture
import heizige.kk.khatkit.app.core.ui.icons.power
import heizige.kk.khatkit.app.core.ui.icons.schedule
import heizige.kk.khatkit.app.core.ui.icons.smartphone
import heizige.kk.khatkit.app.core.ui.icons.tune
import heizige.kk.khatkit.app.core.ui.icons.verifiedUser
import heizige.kk.khatkit.app.core.ui.icons.wavingHand
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

private enum class GreetingStep { Welcome, Agreement, Permissions, AiSetup, Settings }

private const val KEY_GREETING_COMPLETED = "greeting_completed"

/**
 * 首次启动引导：欢迎 → 用户协议 → 权限。
 *
 * 样式参考 KodeHead 的 Greeting：渐变发光标题、步骤切换动画、
 * 顶栏/底栏同色 + 步骤图标、fadingEdge 滚动渐隐；完成或跳过后写入
 * [KEY_GREETING_COMPLETED]。
 */
@Composable
fun GreetingPage(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(GreetingStep.Welcome) }
    var agreementAccepted by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val goBack: () -> Unit = {
        step = when (step) {
            GreetingStep.Welcome -> GreetingStep.Welcome
            GreetingStep.Agreement -> GreetingStep.Welcome
            GreetingStep.Permissions -> GreetingStep.Agreement
            GreetingStep.AiSetup -> GreetingStep.Permissions
            GreetingStep.Settings -> GreetingStep.AiSetup
        }
    }

    val backEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backEventState,
        isBackEnabled = step != GreetingStep.Welcome,
        onBackCompleted = goBack,
    )

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = step != GreetingStep.Welcome,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                GreetingTopAppBar(step = step)
            }
        },
        bottomBar = {
            GreetingBottomBar(
                step = step,
                agreementAccepted = agreementAccepted,
                canScrollForward = scrollState.canScrollForward,
                onBack = goBack,
                onScrollToEnd = {
                    scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                },
                onNext = { step = nextStep(step) },
                onFinish = onFinish,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = step,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    slideInHorizontally { it } togetherWith
                        slideOutHorizontally { (-it * 0.4f).toInt() }
                } else {
                    slideInHorizontally { (-it * 0.4f).toInt() } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut(targetAlpha = 0.6f)
                }
            },
            label = "greetingStep",
        ) { current ->
            when (current) {
                GreetingStep.Welcome -> GreetingWelcomeScreen()
                GreetingStep.Agreement -> GreetingAgreementScreen(
                    scrollState = scrollState,
                    accepted = agreementAccepted,
                    onAcceptedChange = { agreementAccepted = it },
                )

                GreetingStep.Permissions -> GreetingPermissionsScreen()

                GreetingStep.AiSetup -> GreetingAiSetupScreen()

                GreetingStep.Settings -> GreetingSettingsScreen()
            }
        }
    }
}

private fun nextStep(step: GreetingStep): GreetingStep = when (step) {
    GreetingStep.Welcome -> GreetingStep.Agreement
    GreetingStep.Agreement -> GreetingStep.Permissions
    GreetingStep.Permissions -> GreetingStep.AiSetup
    GreetingStep.AiSetup -> GreetingStep.Settings
    GreetingStep.Settings -> GreetingStep.Settings
}

private fun stepIcon(step: GreetingStep): ImageVector = when (step) {
    GreetingStep.Welcome -> wavingHand
    GreetingStep.Agreement -> menuBook
    GreetingStep.Permissions -> verifiedUser
    GreetingStep.AiSetup -> neurology
    GreetingStep.Settings -> tune
}

@Composable
private fun isMiuixStyle(): Boolean = LocalKedgeStyle.current == KedgeStyle.Miuix

@Composable
private fun greetingTitle(step: GreetingStep): String = when (step) {
    GreetingStep.Agreement -> stringResource(R.string.greeting_title_agreement)
    GreetingStep.Permissions -> stringResource(R.string.greeting_title_permissions)
    GreetingStep.AiSetup -> stringResource(R.string.greeting_title_ai)
    GreetingStep.Settings -> stringResource(R.string.greeting_title_settings)
    GreetingStep.Welcome -> ""
}

@Composable
private fun greetingSubtitle(step: GreetingStep): String? = when (step) {
    GreetingStep.Permissions -> stringResource(R.string.greeting_permissions_subtitle)
    GreetingStep.AiSetup -> stringResource(R.string.greeting_ai_subtitle)
    GreetingStep.Settings -> stringResource(R.string.greeting_settings_subtitle)
    else -> null
}

@Composable
private fun GreetingStepBadge(step: GreetingStep) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .background(
                KedgeColors.surfaceVariant.copy(alpha = 0.54f),
                CircleShape,
            )
            .padding(8.dp),
    ) {
        Icon(
            imageVector = stepIcon(step),
            contentDescription = null,
            tint = KedgeColors.onSurfaceVariant.copy(alpha = 0.87f),
        )
    }
}

@Composable
private fun GreetingTopAppBar(step: GreetingStep) {
    if (isMiuixStyle()) {
        KedgeTopAppBar(
            title = greetingTitle(step),
            subtitle = greetingSubtitle(step),
            navigationIcon = { GreetingStepBadge(step) },
        )
        return
    }
    CenterAlignedTopAppBar(
        title = {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (scaleIn(spring(), initialScale = 0.2f) + fadeIn()) togetherWith
                        (scaleOut(spring(), targetScale = 0.2f) + fadeOut())
                },
                label = "greetingTitle",
            ) { current ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = greetingTitle(current),
                        modifier = Modifier.alpha(0.87f),
                    )
                    val subtitle = greetingSubtitle(current)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = KedgeColors.onSurfaceVariant,
                            modifier = Modifier.alpha(0.87f),
                        )
                    }
                }
            }
        },
        navigationIcon = {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        initialScale = 0.2f,
                    ) + fadeIn()) togetherWith
                        (scaleOut(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            targetScale = 0.2f,
                        ) + fadeOut())
                },
                label = "greetingStepIcon",
            ) { current ->
                GreetingStepBadge(current)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors()
            .copy(containerColor = BottomAppBarDefaults.containerColor),
    )
}

@Composable
private fun GreetingBottomBar(
    step: GreetingStep,
    agreementAccepted: Boolean,
    canScrollForward: Boolean,
    onBack: () -> Unit,
    onScrollToEnd: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (step.ordinal + 1).toFloat() / GreetingStep.entries.size.toFloat(),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "progress",
    )

    if (isMiuixStyle()) {
        KedgeSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GreetingBottomActions(
                    step = step,
                    agreementAccepted = agreementAccepted,
                    canScrollForward = canScrollForward,
                    animatedProgress = animatedProgress,
                    onBack = onBack,
                    onScrollToEnd = onScrollToEnd,
                    onNext = onNext,
                    onFinish = onFinish,
                )
            }
        }
    } else {
        BottomAppBar(
            windowInsets = WindowInsets(16.dp, 0.dp, 16.dp, 0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GreetingBottomActions(
                    step = step,
                    agreementAccepted = agreementAccepted,
                    canScrollForward = canScrollForward,
                    animatedProgress = animatedProgress,
                    onBack = onBack,
                    onScrollToEnd = onScrollToEnd,
                    onNext = onNext,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun RowScope.GreetingBottomActions(
    step: GreetingStep,
    agreementAccepted: Boolean,
    canScrollForward: Boolean,
    animatedProgress: Float,
    onBack: () -> Unit,
    onScrollToEnd: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val isReadAction = step == GreetingStep.Agreement && canScrollForward
    val nextEnabled = when (step) {
        GreetingStep.Agreement -> canScrollForward || agreementAccepted
        else -> true
    }

    KedgeTextButton(
        modifier = Modifier.animateContentSize(),
        onClick = onBack,
        enabled = step != GreetingStep.Welcome,
    ) {
        Text(stringResource(R.string.greeting_action_back))
    }
    Spacer(modifier = Modifier.weight(1f))
    KedgeProgressIndicator(
        modifier = Modifier.width(64.dp),
        progress = animatedProgress,
        type = KedgeProgressIndicatorType.Linear,
    )
    Spacer(modifier = Modifier.weight(1f))
    KedgeButton(
        modifier = Modifier.animateContentSize(),
        onClick = {
            when {
                step == GreetingStep.Settings -> onFinish()
                isReadAction -> onScrollToEnd()
                else -> onNext()
            }
        },
        enabled = nextEnabled,
    ) {
        Text(
            when {
                step == GreetingStep.Settings -> stringResource(R.string.greeting_action_finish)
                isReadAction -> stringResource(R.string.greeting_action_read)
                else -> stringResource(R.string.greeting_action_next)
            }
        )
    }
}

@Composable
private fun GreetingWelcomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KedgeColors.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        var textWidth by remember { mutableFloatStateOf(360f) }
        val infiniteTransition = rememberInfiniteTransition(label = "glow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.87f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glowAlpha",
        )
        val glowOffset by infiniteTransition.animateFloat(
            initialValue = -textWidth,
            targetValue = textWidth,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glowOffset",
        )

        val gradientColors = listOf(
            KedgeColors.primary.copy(alpha = glowAlpha),
            KedgeColors.primaryContainer.copy(alpha = glowAlpha),
            KedgeColors.secondary.copy(alpha = glowAlpha),
            KedgeColors.secondaryContainer.copy(alpha = glowAlpha),
            KedgeColors.tertiary.copy(alpha = glowAlpha),
            KedgeColors.tertiaryContainer.copy(alpha = glowAlpha),
        )

        Text(
            text = stringResource(R.string.app_name),
            onTextLayout = { textWidth = it.size.width.toFloat() },
            style = MaterialTheme.typography.titleLargeEmphasized.copy(
                brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset(glowOffset, 0f),
                    end = Offset(glowOffset + textWidth, 0f),
                )
            ),
            fontSize = 64.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GreetingAgreementScreen(
    scrollState: androidx.compose.foundation.ScrollState,
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KedgeColors.background)
            .fadingEdge(top = 32.dp, bottom = 56.dp, strength = 1f)
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.greeting_agreement_text),
            style = MaterialTheme.typography.bodyMedium,
            color = KedgeColors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onAcceptedChange(!accepted) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KedgeCheckbox(
                checked = accepted,
                onCheckedChange = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.greeting_agreement_accept),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun GreetingSettingsScreen() {
    val settingsStore: SettingsStore = rememberAppEntryPoint().settingsStore()
    val provider: KhatKitToolProvider = rememberAppEntryPoint().khatKitToolProvider()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val colorMode = rememberColorMode()
    val scope = rememberCoroutineScope()
    var uiStyle by remember { mutableStateOf(provider.uiStyle) }
    var showCustomColor by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(PresetThemes[0].standardLight.primary) }
    val cards = listCardStyle()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val currentColorModeLabel = when (colorMode.value) {
        ColorMode.SYSTEM -> stringResource(R.string.greeting_settings_theme_system)
        ColorMode.LIGHT -> stringResource(R.string.greeting_settings_theme_light)
        ColorMode.DARK -> stringResource(R.string.greeting_settings_theme_dark)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KedgeColors.background)
            .fadingEdge(top = 32.dp, bottom = 56.dp, strength = 1f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OptionsText(stringResource(R.string.greeting_settings_theme_mode))
        Spacer(modifier = Modifier.height(8.dp))

        ExpandableOptionItem(
            imageVector = brightnessAuto,
            title = stringResource(R.string.greeting_settings_theme_mode),
            subtitle = currentColorModeLabel,
            initiallyExpanded = false,
            contentPadding = PaddingValues(bottom = 12.dp, start = 12.dp, end = 12.dp),
            leadingContent = {
                AnimatedContent(
                    targetState = colorMode.value,
                    transitionSpec = {
                        (scaleIn(spring(), initialScale = 0.2f) + fadeIn()) togetherWith
                            (scaleOut(spring(), targetScale = 0.2f) + fadeOut())
                    },
                    label = "themeModeIcon",
                ) { mode ->
                    Icon(
                        imageVector = when (mode) {
                            ColorMode.SYSTEM -> brightnessAuto
                            ColorMode.LIGHT -> lightMode
                            ColorMode.DARK -> darkMode
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.87f),
                    )
                }
            },
        ) {
            MorphThemeModeSelector(
                selected = colorMode.value,
                onSelect = { colorMode.value = it },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        OptionsText(stringResource(R.string.greeting_settings_theme_color))
        Spacer(modifier = Modifier.height(8.dp))

        ExpandableOptionItem(
            imageVector = palette,
            title = stringResource(R.string.greeting_settings_theme_color),
            contentColor = Color.Transparent,
        ) {
            Column {
                KedgeOptionItem(
                    onClick = {
                        if (dynamicColorSupported) {
                            scope.launch { settingsStore.update { it.copy(dynamicColor = !settings.dynamicColor) } }
                        }
                    },
                    modifier = if (dynamicColorSupported) Modifier
                    else Modifier.alpha(0.5f),
                    shape = cards.groupItemShape(isFirst = true, isLast = false),
                    leadingContent = {
                        Icon(
                            imageVector = palette,
                            contentDescription = null,
                            tint = KedgeColors.onSurfaceVariant.copy(alpha = 0.87f),
                        )
                    },
                    titleContent = { Text(stringResource(R.string.greeting_settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.greeting_settings_dynamic_color_desc)) },
                    trailingContent = {
                        KedgeSwitch(
                            checked = dynamicColorSupported && settings.dynamicColor,
                            enabled = dynamicColorSupported,
                            onCheckedChange = { enabled ->
                                if (dynamicColorSupported) {
                                    scope.launch { settingsStore.update { it.copy(dynamicColor = enabled) } }
                                }
                            },
                        )
                    },
                )

                AnimatedVisibility(
                    visible = !settings.dynamicColor,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        )
                    ) + fadeOut(),
                ) {
                    PresetThemeColorDots(
                        modifier = Modifier
                            .padding(top = cards.gap)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(vertical = 12.dp),
                        selectedThemeId = settings.themeId,
                        onSelectTheme = { themeId ->
                            scope.launch { settingsStore.update { it.copy(themeId = themeId) } }
                        },
                        onCustomColorClick = { showCustomColor = true },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OptionsText(stringResource(R.string.greeting_settings_ui_style))
        Spacer(modifier = Modifier.height(8.dp))

        ExpandableOptionItem(
            imageVector = tune,
            title = stringResource(R.string.greeting_settings_ui_style),
            subtitle = if (uiStyle == KhatKitUiStyle.MATERIAL) "Material 3" else "Miuix",
            contentColor = Color.Transparent,
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(cards.gap)) {
                listOf(
                    KhatKitUiStyle.MATERIAL to "Material 3",
                    KhatKitUiStyle.MIUIX to "Miuix",
                ).forEach { (style, label) ->
                    KedgeOptionItem(
                        onClick = {
                            provider.uiStyle = style
                            uiStyle = style
                        },
                        shape = RoundedCornerShape(4.dp),
                        titleContent = { Text(label) },
                        trailingContent = {
                            KedgeRadioButton(
                                selected = uiStyle == style,
                                onClick = {
                                    provider.uiStyle = style
                                    uiStyle = style
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    ThemeCustomColorSheet(
        visible = showCustomColor,
        initialColor = customColor,
        history = settings.customColorHistory,
        onDismiss = { showCustomColor = false },
        onColorChanged = { customColor = it },
        onConfirm = { color ->
            val hex = String.format("#%08X", color.toArgb())
            val custom = CustomTheme(
                name = hex,
                primaryColorArgb = color.toArgb().toLong() and 0xFFFFFFFFL,
            )
            scope.launch {
                settingsStore.update {
                    it.copy(
                        dynamicColor = false,
                        customThemes = it.customThemes + custom,
                        themeId = custom.id,
                        customColorHistory = (
                            listOf(color.toArgb().toLong() and 0xFFFFFFFFL) +
                                it.customColorHistory.filter { c -> c != (color.toArgb().toLong() and 0xFFFFFFFFL) }
                            ).take(8),
                    )
                }
            }
            showCustomColor = false
        },
    )
}

@Composable
private fun GreetingPermissionsScreen() {
    PermissionChecklist()
}

@Composable
private fun GreetingAiSetupScreen() {
    val settingsStore: SettingsStore = rememberAppEntryPoint().settingsStore()
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KedgeColors.background)
            .fadingEdge(top = 32.dp, bottom = 56.dp, strength = 1f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(cards.gap),
    ) {
        item(key = "custom_header") {
            OptionsText(stringResource(R.string.greeting_ai_enabled_models))
        }
        items(configuredProviders, key = { it.id }) { provider ->
            AiProviderItem(
                provider = provider,
                configured = true,
                shape = cards.indexedShape(
                    configuredProviders.indexOf(provider),
                    configuredProviders.size + 1,
                ),
                modifier = Modifier.animateItem(),
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

        item(key = "custom_button") {
            KedgeOptionItem(
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
                        tint = KedgeColors.onSurfaceVariant.copy(alpha = 0.87f),
                    )
                },
                titleContent = { Text(stringResource(R.string.greeting_ai_custom_key)) },
                supportingContent = { Text(stringResource(R.string.greeting_ai_custom_desc)) },
            )
        }

        item(key = "providers_header") {
            Spacer(modifier = Modifier.height(10.dp))
            OptionsText(stringResource(R.string.greeting_ai_providers))
        }
        items(unconfiguredProviders, key = { it.id }) { provider ->
            AiProviderItem(
                provider = provider,
                configured = false,
                shape = cards.indexedShape(
                    unconfiguredProviders.indexOf(provider),
                    unconfiguredProviders.size,
                ),
                modifier = Modifier.animateItem(),
                onClick = {
                    draftKey = provider.apiKeyOrEmpty()
                    draftBaseUrl = provider.baseUrlOrEmpty()
                    sheetProvider = provider
                },
                onToggle = {},
            )
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ProviderSetting.Types.forEachIndexed { index, type ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ProviderSetting.Types.size,
                                ),
                                label = { Text(type.simpleName ?: "") },
                                selected = customType == type,
                                onClick = {
                                    customType = type
                                    if (draftBaseUrl.isBlank()) {
                                        draftBaseUrl = defaultBaseUrlFor(type)
                                    }
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text(stringResource(R.string.greeting_ai_custom_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = draftKey,
                    onValueChange = { draftKey = it },
                    label = { Text(stringResource(R.string.greeting_ai_api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draftBaseUrl,
                    onValueChange = { draftBaseUrl = it },
                    label = { Text(stringResource(R.string.greeting_ai_base_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showSaveButton) {
                    KedgeButton(
                        onClick = save,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.greeting_ai_save))
                    }
                }
            }
        }
        if (isMiuixStyle()) {
            KedgeModalBottomSheet(
                show = true,
                title = sheetTitle,
                onDismissRequest = closeSheet,
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
    KedgeOptionItem(
        onClick = onClick,
        shape = shape,
        modifier = modifier,
        leadingContent = {
            Icon(
                imageVector = neurology,
                contentDescription = null,
                tint = KedgeColors.onSurfaceVariant.copy(alpha = 0.87f),
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
            KedgeSwitch(
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
}

private fun ProviderSetting.baseUrlOrEmpty(): String = when (this) {
    is ProviderSetting.OpenAI -> baseUrl
    is ProviderSetting.Google -> baseUrl
    is ProviderSetting.Claude -> baseUrl
}

private fun ProviderSetting.withEnabled(enabled: Boolean): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(enabled = enabled)
    is ProviderSetting.Google -> copy(enabled = enabled)
    is ProviderSetting.Claude -> copy(enabled = enabled)
}

private fun ProviderSetting.withConfig(apiKey: String, baseUrl: String): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(apiKey = apiKey, baseUrl = baseUrl.ifBlank { this.baseUrl }, enabled = true)
    is ProviderSetting.Google -> copy(apiKey = apiKey, baseUrl = baseUrl.ifBlank { this.baseUrl }, enabled = true)
    is ProviderSetting.Claude -> copy(apiKey = apiKey, baseUrl = baseUrl.ifBlank { this.baseUrl }, enabled = true)
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
