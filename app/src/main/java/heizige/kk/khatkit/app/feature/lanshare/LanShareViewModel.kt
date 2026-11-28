package heizige.kk.khatkit.app.feature.lanshare

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import heizige.kk.khatkit.app.feature.lanshare.helper.LanShareManager

@HiltViewModel
internal class LanShareViewModel @Inject constructor(val manager: LanShareManager) : ViewModel()
