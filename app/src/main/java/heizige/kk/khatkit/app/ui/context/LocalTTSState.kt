package heizige.kk.khatkit.app.ui.context

import androidx.compose.runtime.compositionLocalOf
import heizige.kk.khatkit.app.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
