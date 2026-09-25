package heizige.kk.khatkit.app.core.ui.context

import androidx.compose.runtime.compositionLocalOf
import heizige.kk.khatkit.app.core.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

