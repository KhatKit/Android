package heizige.kk.khatkit.app.ui.context

import androidx.compose.runtime.compositionLocalOf
import heizige.kk.khatkit.app.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

