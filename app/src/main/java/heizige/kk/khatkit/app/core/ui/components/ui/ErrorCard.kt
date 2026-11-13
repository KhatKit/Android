package heizige.kk.khatkit.app.core.ui.components.ui

import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSurface
import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.feature.chat.ChatError
import heizige.kk.khatkit.app.feature.chat.ChatErrorSolution
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.delete
import ui.feice.mochi.theme.MochiTheme

@Composable
fun ErrorCardsDisplay(
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MochiTheme.colorScheme
    AnimatedVisibility(
        visible = errors.isNotEmpty(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 清除全部按钮（当有多个错误时显示）
            if (errors.size > 1) {
                KkSurface(
                    onClick = onClearAllErrors,
                    shape = MochiTheme.shapes.small,
                    color = colors.errorContainer.copy(alpha = 0.8f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.chat_page_clear_all_errors),
                            style = MochiTheme.textStyles.labelSmall,
                            color = colors.onErrorContainer,
                        )
                    }
                }
            }

            // 错误卡片列表
            errors.forEach { error ->
                ErrorCard(
                    error = error,
                    onDismiss = { onDismissError(error.id) },
                )
            }
        }
    }
}

@Composable
fun ErrorCard(
    error: ChatError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val checkFastModelSettings = stringResource(R.string.chat_page_check_fast_model_settings)
    val colors = MochiTheme.colorScheme
    val linkColor = colors.primary

    // 5 秒后自动消失
    LaunchedEffect(error.id) {
        delay(5000)
        onDismiss()
    }

    KkSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MochiTheme.shapes.medium,
        color = colors.errorContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (error.title != null) {
                    Text(
                        text = error.title,
                        style = MochiTheme.textStyles.body,
                        color = colors.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = error.error.message ?: "Unknown error",
                    style = MochiTheme.textStyles.body,
                    color = colors.onErrorContainer.copy(alpha = 0.8f),
                    overflow = TextOverflow.Ellipsis,
                )
                if (error.solution == ChatErrorSolution.CheckFastModelSettings) {
                    Text(
                        text = buildAnnotatedString {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "check_fast_model_settings",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline,
                                        )
                                    ),
                                    linkInteractionListener = {
                                        navController.navigate(Screen.SettingModels)
                                    },
                                )
                            ) {
                                append(checkFastModelSettings)
                            }
                        },
                        style = MochiTheme.textStyles.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MochiIconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(
                                clipData = ClipData.newPlainText("Error", error.error.message ?: "Unknown error")
                            )
                        )
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = contentCopy,
                    contentDescription = "Copy error message",
                    tint = colors.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            MochiIconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = close,
                    contentDescription = stringResource(R.string.chat_page_dismiss_error),
                    tint = colors.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
