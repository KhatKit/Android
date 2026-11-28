package heizige.kk.khatkit.app.feature.settings.helper

import heizige.kk.khatkit.app.feature.settings.data.DonationSnapshot
import heizige.kk.khatkit.app.feature.settings.data.DonationSupporterPage
import heizige.kk.khatkit.app.feature.settings.data.DonationUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owned by the ViewModel scope; generations prevent cancelled requests from publishing stale data. */
internal class DonationLoadManager(
    private val scope: CoroutineScope,
    private val baseUrl: () -> String,
    private val cached: suspend (String) -> DonationSnapshot?,
    private val fetch: suspend (String) -> DonationSnapshot,
    private val fetchSupporters: suspend (String, Int) -> DonationSupporterPage,
) {
    private val mutableState = MutableStateFlow(DonationUiState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var supportersJob: Job? = null
    private var currentBase = ""
    private var generation = 0L

    fun refresh() {
        val base = baseUrl()
        if (refreshJob?.isActive == true && base == currentBase) return
        refreshJob?.cancel()
        supportersJob?.cancel()
        val version = ++generation
        val previous = mutableState.value.snapshot.takeIf { base == currentBase }
        currentBase = base
        mutableState.value = DonationUiState(snapshot = previous)
        refreshJob = scope.launch {
            try {
                if (previous == null) {
                    val snapshot = cached(base)
                    if (version != generation) return@launch
                    mutableState.value = mutableState.value.copy(snapshot = snapshot)
                }
                val snapshot = fetch(base)
                if (version == generation) mutableState.value = DonationUiState(snapshot = snapshot, loading = false, loaded = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == generation) mutableState.value = mutableState.value.copy(loading = false, failed = true)
            }
        }
    }

    fun loadSupporters(retry: Boolean = false) {
        val previous = mutableState.value
        if (previous.loading || supportersJob?.isActive == true) return
        if (previous.wallLoaded && previous.nextOffset >= previous.total) return
        if (previous.wallFailed && !retry) return
        val version = generation
        val base = currentBase
        supportersJob = scope.launch {
            mutableState.value = mutableState.value.copy(wallLoading = true, wallFailed = false)
            try {
                val page = fetchSupporters(base, previous.nextOffset)
                if (version != generation) return@launch
                mutableState.value = mutableState.value.copy(
                    supporters = (previous.supporters + page.items).distinctBy { it.id },
                    total = page.total,
                    nextOffset = page.offset + page.items.size,
                    wallLoaded = true,
                    wallLoading = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == generation) mutableState.value = mutableState.value.copy(wallLoading = false, wallFailed = true)
            }
        }
    }
}
