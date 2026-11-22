package heizige.kk.khatkit.app.feature.lanshare

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class LanShareViewModel @Inject constructor(val manager: LanShareManager) : ViewModel()
