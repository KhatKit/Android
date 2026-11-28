package heizige.kk.khatkit.app.feature.settings.data

import heizige.kk.khatkit.app.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.icons.utility.checkCircle
import heizige.kk.khatkit.app.core.ui.icons.files.download
import heizige.kk.khatkit.app.core.ui.icons.navigation.refresh
import heizige.kk.khatkit.app.core.ui.icons.utility.warning
import heizige.kk.khatkit.app.core.util.UpdateCheckResponse
import heizige.kk.khatkit.uikit.mochi.kkPressState
import ui.feice.mochi.basic.MochiProgressRing
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.mochiBounce
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme


/** 检查更新这一件事的状态机：空闲 / 检查中 / 已最新 / 有新版本 / 失败。 */
internal sealed interface AboutUpdateState {
    data object Idle : AboutUpdateState
    data object Checking : AboutUpdateState
    data object UpToDate : AboutUpdateState
    data class Available(val info: UpdateCheckResponse) : AboutUpdateState
    data class Failed(val message: String) : AboutUpdateState
}
