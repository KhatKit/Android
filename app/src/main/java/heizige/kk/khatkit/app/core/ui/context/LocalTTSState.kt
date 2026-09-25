package heizige.kk.khatkit.app.core.ui.context

import androidx.compose.runtime.compositionLocalOf
import heizige.kk.khatkit.app.core.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
