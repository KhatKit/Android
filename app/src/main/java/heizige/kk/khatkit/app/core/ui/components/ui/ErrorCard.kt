package heizige.kk.khatkit.app.core.ui.components.ui

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.feature.chat.ChatError
import heizige.kk.khatkit.app.feature.chat.ChatErrorSolution
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.feice.mochi.anim.MochiMotionTokens
import ui.feice.mochi.anim.mochiReduceMotion
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiToastColors
import ui.feice.mochi.basic.MochiToastDefaults
import ui.feice.mochi.effect.shadow.drawMochiShadow
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

private val ErrorCardCornerRadius = 26.dp
private val ErrorCardShape = MochiRoundedRectangle(ErrorCardCornerRadius)

@Composable
fun ErrorCardsDisplay(
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = mochiReduceMotion()
    val visible = errors.isNotEmpty()
    val origin = TransformOrigin(0.5f, 1f)
    // 动画退出期间保留上一帧的卡片内容。
    var lastErrors by remember { mutableStateOf(errors) }
    SideEffect { if (visible) lastErrors = errors }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        enter = if (reduceMotion) EnterTransition.None else
            scaleIn(MochiMotionTokens.Present, initialScale = 0.96f, transformOrigin = origin) +
                fadeIn(MochiMotionTokens.Fade),
        exit = if (reduceMotion) ExitTransition.None else
            scaleOut(MochiMotionTokens.Settle, targetScale = 0.96f, transformOrigin = origin) +
                fadeOut(MochiMotionTokens.FadeOut),
    ) {
        val displayedErrors = if (visible) errors else lastErrors
        val scheme = MochiTheme.colorScheme
        val errorColors = MochiToastDefaults.colors(
            container = lerp(scheme.surface, scheme.error, if (scheme.isDark) 0.34f else 0.22f),
        )
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (displayedErrors.size > 1) {
                MochiButton(
                    onClick = onClearAllErrors,
                    enabled = visible,
                    colors = MochiButtonDefaults.colors().copy(
                        container = errorColors.container,
                        content = errorColors.content,
                        disabledContainer = errorColors.container,
                    ),
                    minHeight = 44.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    MochiIonicon("trash", size = 16.dp, tint = errorColors.content)
                    BasicText(
                        text = stringResource(R.string.chat_page_clear_all_errors),
                        style = MochiTheme.textStyles.labelMedium.copy(color = errorColors.content),
                    )
                }
            }

            displayedErrors.forEach { error ->
                key(error.id) {
                    ErrorCard(
                        error = error,
                        onDismiss = { onDismissError(error.id) },
                        active = visible,
                        colors = errorColors,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    error: ChatError,
    onDismiss: () -> Unit,
    active: Boolean,
    colors: MochiToastColors,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scheme = MochiTheme.colorScheme
    val shadow = MochiTheme.shadows.lifted
    val message = error.error.message ?: "Unknown error"
    val dismiss by rememberUpdatedState(onDismiss)
    val actionBackground = colors.content.copy(alpha = 0.11f)
    val actionColors = MochiButtonDefaults.colors(MochiButtonStyle.Tonal).copy(
        container = actionBackground,
        content = colors.content,
        disabledContainer = actionBackground,
    )

    LaunchedEffect(active) {
        if (active) {
            delay(5000)
            dismiss()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithCache {
                val resolvedShadow = shadow.resolve(this, scheme.isDark)
                val cornerRadius = ErrorCardCornerRadius.toPx()
                onDrawBehind { drawMochiShadow(size.toRect(), cornerRadius, resolvedShadow, scheme.shadow) }
            }
            .background(colors.container, ErrorCardShape)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(scheme.error, MochiTheme.shapes.full),
            contentAlignment = Alignment.Center,
        ) {
            MochiIonicon("alert-circle", size = 20.dp, tint = scheme.onError)
        }
        Column(
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val titleStyle = MochiTheme.textStyles.labelLarge.copy(
                color = colors.content,
                fontWeight = FontWeight.SemiBold,
            )
            error.title?.let { title ->
                BasicText(
                    text = title,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(
                text = message,
                style = if (error.title == null) titleStyle else
                    MochiTheme.textStyles.bodySmall.copy(color = colors.secondary),
                overflow = TextOverflow.Ellipsis,
            )
            if (error.solution == ChatErrorSolution.CheckFastModelSettings) {
                val navController = LocalNavController.current
                val label = stringResource(R.string.chat_page_check_fast_model_settings)
                BasicText(
                    text = buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "check_fast_model_settings",
                                styles = TextLinkStyles(
                                    style = SpanStyle(textDecoration = TextDecoration.Underline),
                                )
                            ) { if (active) navController.navigate(Screen.SettingModels) }
                        ) { append(label) }
                    },
                    style = MochiTheme.textStyles.labelMedium.copy(color = colors.content),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MochiIconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Error", message)))
                    }
                },
                enabled = active,
                colors = actionColors,
                size = 44.dp,
            ) {
                MochiIonicon(
                    "copy",
                    contentDescription = "Copy error message",
                    tint = colors.content,
                    size = 18.dp,
                )
            }
            MochiIconButton(
                onClick = onDismiss,
                enabled = active,
                colors = actionColors,
                size = 44.dp,
            ) {
                MochiIonicon(
                    "close",
                    contentDescription = stringResource(R.string.chat_page_dismiss_error),
                    tint = colors.content,
                    size = 20.dp,
                )
            }
        }
    }
}
