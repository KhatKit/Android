package heizige.kk.khatkit.app.feature.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.uikit.mochi.MochiMenuPopup
import heizige.kk.khatkit.uikit.mochi.MochiMenuRow
import heizige.kk.khatkit.uikit.mochi.kkPressState
import heizige.kk.khatkit.uikit.mochi.rememberKkHaptics
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiIconDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiTrigger
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.theme.MochiTheme

private val ButtonSize = 48.dp
private const val ALLOW_ONCE = "仅本次允许"
private const val ALLOW_TEN_MINUTES = "10 分钟内自动允许"
private const val MORE_OPTIONS = "更多允许选项"

@Composable
internal fun ApprovalButtons(systemWindow: Boolean, onApprove: () -> Unit, onDeny: () -> Unit, onRemember: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }
    fun answer(action: () -> Unit) {
        if (answered) return
        answered = true
        expanded = false
        action()
    }
    val scheme = MochiTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        MochiIconButton(
            onClick = { answer(onDeny) }, enabled = !answered, size = ButtonSize,
            colors = MochiButtonDefaults.colors(MochiButtonStyle.Tonal).copy(container = scheme.errorContainer, content = scheme.onErrorContainer),
            modifier = Modifier.approvalKeys(!answered, { answer(onDeny) }).semantics { contentDescription = "拒绝" },
        ) { MochiIonicon("close", size = 20.dp, tint = scheme.onErrorContainer) }

        if (!systemWindow) {
            MochiIconDropdown(
                expanded = expanded, onExpandedChange = { expanded = it && !answered },
                items = persistentListOf(MochiItem(ALLOW_ONCE), MochiItem(ALLOW_TEN_MINUTES)),
                onItemClick = { index ->
                    when (index) {
                        0 -> answer(onApprove)
                        1 -> answer(onRemember)
                    }
                },
                onClick = { answer(onApprove) }, enabled = !answered, size = ButtonSize,
                behavior = MochiDropdownDefaults.iconBehavior().copy(trigger = MochiTrigger.LongPress),
                colors = MochiDropdownDefaults.iconDropdownColors(scheme.primary, scheme.onPrimary),
                modifier = Modifier.approvalKeys(!answered, { answer(onApprove) }, { expanded = true }).semantics {
                    contentDescription = "允许"
                    onClick(label = ALLOW_ONCE, action = null)
                    onLongClick(label = MORE_OPTIONS, action = null)
                },
            ) { MochiIonicon("checkmark", size = 20.dp, tint = scheme.onPrimary) }
        } else {
            // Native Mochi menus need a full Activity decor; a service window only wraps the pill.
            val interaction = remember { MutableInteractionSource() }
            val haptics = rememberKkHaptics()
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(ButtonSize).kkPressState(interaction).background(scheme.primary, MochiTheme.shapes.full)
                        .semantics { contentDescription = "允许" }
                        .combinedClickable(
                            interactionSource = interaction, indication = null, enabled = !answered, role = Role.Button,
                            onLongClickLabel = MORE_OPTIONS, onClickLabel = ALLOW_ONCE, hapticFeedbackEnabled = false,
                            onLongClick = { haptics.longPress(); expanded = true }, onClick = { answer(onApprove) },
                        ),
                    contentAlignment = Alignment.Center,
                ) { MochiIonicon("checkmark", size = 20.dp, tint = scheme.onPrimary) }
                MochiMenuPopup(expanded, { expanded = false }, modifier = Modifier.width(IntrinsicSize.Max)) {
                    MochiMenuRow(enabled = expanded, text = { Text(ALLOW_ONCE) }, onClick = { answer(onApprove) })
                    MochiMenuRow(enabled = expanded, text = { Text(ALLOW_TEN_MINUTES) }, onClick = { answer(onRemember) })
                }
            }
        }
    }
}

/** Mochi exposes semantics and pointer gestures; external keyboards need focus and key activation too. */
private fun Modifier.approvalKeys(enabled: Boolean, activate: () -> Unit, menu: (() -> Unit)? = null): Modifier =
    onPreviewKeyEvent { event ->
        if (!enabled) return@onPreviewKeyEvent false
        val action = when (event.key) {
            Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter -> activate
            Key.Menu -> menu
            else -> null
        } ?: return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp) action()
        true
    }.focusable(enabled)
