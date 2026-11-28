package heizige.kk.khatkit.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.feature.settings.helper.DonationQrHelper
import heizige.kk.khatkit.uikit.mochi.Toast
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DonationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val donationRepository: heizige.kk.khatkit.app.feature.settings.data.DonationRepository,
) : ViewModel() {
    val pendingQr = savedStateHandle.getStateFlow<Int?>(PENDING_QR, null)
    private val savingState = MutableStateFlow(false)
    val saving = savingState.asStateFlow()

    private val sponsorLoader = heizige.kk.khatkit.app.feature.settings.helper.DonationLoadManager(
        scope = viewModelScope,
        baseUrl = donationRepository::baseUrl,
        cached = donationRepository::cached,
        fetch = donationRepository::refresh,
        fetchSupporters = donationRepository::supporters,
    )
    internal val sponsors = sponsorLoader.state

    init { refreshSponsors() }

    internal fun refreshSponsors() = sponsorLoader.refresh()
    internal fun loadSupporters(retry: Boolean = false) = sponsorLoader.loadSupporters(retry)

    fun prepareExport(@DrawableRes drawable: Int): Boolean {
        if (savingState.value || pendingQr.value != null) return false
        savedStateHandle[PENDING_QR] = drawable
        return true
    }

    fun cancelExport() {
        savedStateHandle[PENDING_QR] = null
    }

    fun exportTo(uri: Uri?) {
        val drawable = pendingQr.value
        cancelExport()
        if (uri == null || drawable == null || savingState.value) return
        savingState.value = true
        // The ViewModel owns the job so rotating the screen does not interrupt the write.
        viewModelScope.launch {
            val progress = Toast.showProgress(context.getString(R.string.image_preview_saving))
            var saved = false
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Cannot open donation QR output")
                    output.use { DonationQrHelper.writePng(context, drawable, it) }
                }
                saved = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Show a localized error after releasing the progress toast.
            } finally {
                if (!saved) {
                    // CreateDocument made a new file; do not leave an empty/partial PNG on failure.
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { context.contentResolver.delete(uri, null, null) }
                    }
                }
                progress.dismiss()
                savingState.value = false
            }
            Toast.show(
                message = context.getString(
                    if (saved) R.string.image_preview_saved else R.string.donate_page_save_failed,
                ),
                isError = !saved,
            )
        }
    }

    private companion object {
        const val PENDING_QR = "donation_pending_qr"
    }
}
