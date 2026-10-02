/*
 * 选择器状态持有者。对应上游的 `MediaPickerComponent`（Apache-2.0, T8RIN），
 * 去掉了 Decompose + Hilt，直接由 Compose 的生命周期作用域驱动。
 */

package heizige.kk.khatkit.mediapicker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.SupervisorJob
import heizige.kk.khatkit.mediapicker.R
import heizige.kk.khatkit.mediapicker.data.AndroidMediaRetriever
import heizige.kk.khatkit.mediapicker.data.mediaDateLabel
import heizige.kk.khatkit.mediapicker.domain.ALL_ALBUM_ID
import heizige.kk.khatkit.mediapicker.domain.Album
import heizige.kk.khatkit.mediapicker.domain.AlbumState
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia
import heizige.kk.khatkit.mediapicker.domain.Media
import heizige.kk.khatkit.mediapicker.domain.MediaDateGroup
import heizige.kk.khatkit.mediapicker.domain.MediaDisplaySettings
import heizige.kk.khatkit.mediapicker.domain.MediaItem
import heizige.kk.khatkit.mediapicker.domain.MediaState
import heizige.kk.khatkit.mediapicker.domain.MediaRetriever
import heizige.kk.khatkit.mediapicker.domain.groupMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 选择器状态。
 *
 * [selectedMedia] 是唯一的选中态来源：网格、粘性头组选、全屏预览都改它。
 * [selectedAlbumId] 也归这里管 —— UI 只读它，不自己再存一份，否则两边会漂。
 */
@Stable
class MediaPickerState(
    private val retriever: MediaRetriever,
    scope: CoroutineScope,
    private val todayLabel: String,
    private val yesterdayLabel: String,
) {
    private var scope: CoroutineScope = scope

    /** 换成宿主给的、跟随界面生命周期的作用域。 */
    fun attachScope(scope: CoroutineScope) {
        this.scope = scope
    }

    /** 已选中的媒体。顺序即选择顺序，序号角标按它算。 */
    val selectedMedia = mutableStateListOf<Media>()

    private val sourceMediaState = MutableStateFlow(MediaState())
    private val searchKeyword = MutableStateFlow("")
    private val _displaySettings = MutableStateFlow(MediaDisplaySettings())
    val displaySettings = _displaySettings.asStateFlow()

    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState = _mediaState.asStateFlow()

    private val _filteredMediaState = MutableStateFlow(MediaState())
    val filteredMediaState = _filteredMediaState.asStateFlow()

    private val _albumsState = MutableStateFlow(AlbumState())
    val albumsState = _albumsState.asStateFlow()

    private val _selectedAlbumId = MutableStateFlow(ALL_ALBUM_ID)
    val selectedAlbumId = _selectedAlbumId.asStateFlow()

    private var allowedMedia: AllowedMedia = AllowedMedia.Photos(null)

    private var albumJob: Job? = null
    private var mediaJob: Job? = null
    private var pipelineJob: Job? = null
    private var disposed = false
    private var mediaGeneration = 0L

    fun init(allowedMedia: AllowedMedia) {
        this.allowedMedia = allowedMedia
        getAlbums(allowedMedia)
        getMedia(_selectedAlbumId.value, allowedMedia)
    }

    fun getAlbum(albumId: Long) {
        _selectedAlbumId.value = albumId
        getMedia(albumId, allowedMedia)
    }

    fun updateDisplaySettings(settings: MediaDisplaySettings) {
        _displaySettings.value = settings
    }

    fun filterMedia(keyword: String, isForceReset: Boolean) {
        searchKeyword.value = if (isForceReset) "" else keyword
    }

    private fun getAlbums(allowedMedia: AllowedMedia) {
        albumJob?.cancel()
        albumJob = scope.launch {
            retriever.getAlbumsWithType(allowedMedia)
                .collectLatest { result ->
                    val data = result.getOrNull().orEmpty()
                    val error = result.errorMessage()
                    _albumsState.value = if (data.isEmpty()) {
                        AlbumState(albums = listOf(Album.All), error = error)
                    } else {
                        AlbumState(albums = listOf(Album.All) + data, error = error)
                    }
                }
        }
    }

    private fun getMedia(albumId: Long, allowedMedia: AllowedMedia) {
        // 用代号而不是「先置 loading 再 cancelAndJoin」：cancel() 到旧协程真正停下
        // 之间有一小段窗口，旧 collectLatest 里那次赋值不是挂起调用，仍会执行，
        // 把 isLoading = false 盖在新相册的 loading 态上。
        val generation = ++mediaGeneration
        sourceMediaState.value = sourceMediaState.value.copy(isLoading = true)
        mediaJob?.cancel()
        mediaJob = scope.launch {
            retriever.mediaFlowWithType(albumId, allowedMedia)
                .collectLatest { result ->
                    if (generation != mediaGeneration) return@collectLatest
                    val data = (result.getOrNull()?.let { list ->
                        val ext = (allowedMedia as? AllowedMedia.Photos)?.ext
                        if (ext != null && ext != "*") {
                            list.filter { it.label.endsWith(ext, ignoreCase = true) }
                        } else {
                            list
                        }
                    } ?: emptyList()).distinctBy { it.id }
                    sourceMediaState.value = MediaState(
                        media = data,
                        error = result.errorMessage(),
                        isLoading = false,
                    )
                }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        albumJob?.cancel()
        mediaJob?.cancel()
        pipelineJob?.cancel()
    }

    /**
     * 排序 → 过滤 → 分组三段流水线。输入有四路：原始数据、搜索词、分组设置、排序维度。
     *
     * 排序只挂在 `mediaOrder` 上（`distinctUntilChanged`）：[MediaOrder] 的子类没有
     * `equals`，只看整个 `MediaDisplaySettings` 的话，改个分组粒度也会把整库重排一遍。
     */
    fun startPipeline() {
        if (pipelineJob != null || disposed) return
        pipelineJob = scope.launch {
            val settingsAndKeyword = combine(
                _displaySettings,
                searchKeyword,
            ) { settings, keyword -> settings to keyword }

            val sortedMedia = combine(
                sourceMediaState,
                _displaySettings.map { it.mediaOrder }.distinctUntilChanged(),
            ) { state, order -> state to order }
                .mapLatest { (state, order) ->
                    withContext(Dispatchers.Default) {
                        state.copy(media = order.sortMedia(state.media)) to order
                    }
                }

            combine(sortedMedia, settingsAndKeyword) { (sortedState, order), (settings, keyword) ->
                Triple(sortedState, order, settings to keyword)
            }.collectLatest { (sortedState, order, settingsAndKw) ->
                val (settings, keyword) = settingsAndKw
                // 排序维度在重算途中被改掉了，丢弃这一帧结果
                if (order != settings.mediaOrder) return@collectLatest

                val (media, filtered) = withContext(Dispatchers.Default) {
                    val mappedAll = sortedState.withMappedMedia(settings)
                    val filteredState = if (keyword.isBlank()) {
                        mappedAll
                    } else {
                        sortedState.copy(
                            media = sortedState.media.filter { it.label.matchesKeyword(keyword) }
                        ).withMappedMedia(settings)
                    }
                    mappedAll to filteredState
                }
                _mediaState.value = media
                _filteredMediaState.value = filtered
            }
        }
    }

    private fun MediaState.withMappedMedia(settings: MediaDisplaySettings): MediaState {
        val mapped = media.groupMedia(settings) { timestamp, group ->
            when (group) {
                // 固定格式的分组标题，不跟随用户 locale 的数字系统变化
                MediaDateGroup.Year ->
                    SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(timestamp * 1000))

                MediaDateGroup.Month ->
                    SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(timestamp * 1000))

                else -> timestamp.mediaDateLabel(
                    stringToday = todayLabel,
                    stringYesterday = yesterdayLabel,
                )
            }
        }
        return copy(
            media = if (settings.dateGroup == MediaDateGroup.None) {
                media
            } else {
                mapped.filterIsInstance<MediaItem.MediaViewItem>().map { it.media }
            },
            mappedMedia = mapped,
        )
    }

    private fun String.matchesKeyword(keyword: String): Boolean = when {
        keyword.startsWith("*") -> endsWith(keyword.drop(1), ignoreCase = true)
        keyword.endsWith("*") -> startsWith(keyword.dropLast(1), ignoreCase = true)
        else -> contains(keyword, ignoreCase = true)
    }

    private fun Result<*>.errorMessage(): String =
        if (isFailure) exceptionOrNull()?.message ?: "An error occurred" else ""
}

/**
 * 记住一个跟随界面生命周期的 [MediaPickerState]。
 *
 * 用 `repeatOnLifecycle(STARTED)` 而不是裸 scope：选择器退到后台后 MediaStore 的
 * `ContentObserver` 仍在回调，不收摊的话会对着没人看的界面反复重查全库。
 */
@Composable
fun rememberMediaPickerState(): MediaPickerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val todayLabel = stringResource(R.string.media_picker_today)
    val yesterdayLabel = stringResource(R.string.media_picker_yesterday)

    val state = remember {
        MediaPickerState(
            retriever = AndroidMediaRetriever(context),
            scope = CoroutineScope(Dispatchers.Main.immediate),
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel,
        )
    }

    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    state.attachScope(scope)

    DisposableEffect(state, scope, lifecycleOwner) {
        // repeatOnLifecycle 内部已经处理了「当前已 STARTED」的首次进入
        val job = scope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.startPipeline()
            }
        }
        onDispose {
            job.cancel()
            state.dispose()
        }
    }

    return state
}
