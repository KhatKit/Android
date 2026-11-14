/*
 * 选择器根内容与对外入口。
 *
 * 结构与 ImageToolbox 的 `MediaPickerRootContentEmbeddable`（Apache-2.0, T8RIN）一致：
 * 顶栏（返回 + 标题 + 相册行）→ 网格浮层 → 全屏预览。
 * 权限不足时整页换成引导页。
 */

package heizige.kk.khatkit.mediapicker.ui

import androidx.compose.material3.Scaffold
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import ui.feice.mochi.effect.glass.MochiGlassSurface
import ui.feice.mochi.effect.glass.rememberMochiBackdrop
import ui.feice.mochi.theme.MochiTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import heizige.kk.khatkit.mediapicker.R
import heizige.kk.khatkit.mediapicker.domain.ALL_ALBUM_ID
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia
import heizige.kk.khatkit.mediapicker.domain.Media
import heizige.kk.khatkit.mediapicker.ui.components.MediaAlbumRow
import heizige.kk.khatkit.mediapicker.ui.components.MediaFilterSheet
import heizige.kk.khatkit.mediapicker.ui.components.MediaImagePager
import heizige.kk.khatkit.mediapicker.ui.components.MediaPickerGridWithOverlays
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.navigation.MochiTitleBar

/**
 * 全屏媒体选择器。
 *
 * 宿主把 [visible] 置 true 即可弹出一个全屏 Dialog：
 *
 * ```
 * var showPicker by remember { mutableStateOf(false) }
 * KhatKitMediaPicker(
 *     visible = showPicker,
 *     onDismiss = { showPicker = false },
 *     onPicked = { uris ->
 *         showPicker = false   // 选中不会自动关闭，宿主自己决定
 *     },
 * )
 * ```
 *
 * @param allowedMedia 允许选的媒体类型；[AllowedMedia.Photos.ext] 可按扩展名收窄。
 * @param allowMultiple false = 单选：再次点选会替换掉上一张，且不显示「取消选择」小 FAB。
 * @param title 顶栏标题，null 时用按 [allowMultiple] 生成的默认文案。
 */
@Composable
fun KhatKitMediaPicker(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPicked: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    allowedMedia: AllowedMedia = AllowedMedia.Photos(null),
    allowMultiple: Boolean = true,
    title: String? = null,
    /** 网格第一格「拍照」的点击；null 时不出这一格。 */
    onTakePicture: (() -> Unit)? = null,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // 选择器是 Dialog 的独立窗口：Activity 那套 Mochi 系统栏处理不会作用到这里，
        // 不显式接一下，状态栏 / 导航栏就还是系统默认色。
        MochiDialogSystemBars()
        MediaPickerRootContent(
            onDismiss = onDismiss,
            onPicked = onPicked,
            modifier = modifier.fillMaxSize(),
            allowedMedia = allowedMedia,
            allowMultiple = allowMultiple,
            title = title,
            onTakePicture = onTakePicture,
        )
    }
}

/**
 * 顶栏 + 相册行那一整块的底色。
 *
 * 原来写死 MD3 的 `surfaceContainer`，Miuix 下那块底色会和 Miuix 页面底色对不上
 * （Miuix 主题只桥接了配色，`MaterialTheme.colorScheme` 仍是 MD3 那套）。
 */
/**
 * Dialog 窗口的系统栏：透明 + 图标深浅跟主题，内容（顶栏 / 底部栏）自己铺到底下。
 * Android 15+ 强制 edge-to-edge，statusBarColor 已无效，所以只设透明度与图标明暗。
 */
@Composable
private fun MochiDialogSystemBars() {
    val view = LocalView.current
    val dark = MochiTheme.colorScheme.isDark
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
internal fun MediaPickerRootContent(
    onDismiss: () -> Unit,
    onPicked: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    allowedMedia: AllowedMedia = AllowedMedia.Photos(null),
    allowMultiple: Boolean = true,
    title: String? = null,
    onTakePicture: (() -> Unit)? = null,
) {
    val pickerState = rememberMediaPickerState()
    val permissionState = rememberMediaPermissionState(allowedMedia)

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var previewUri by rememberSaveable { mutableStateOf<String?>(null) }
    // 网格往下滚时收起相册芯片行（工具台同一套行为），回到顶部再展开。
    var chipsCollapsed by remember { mutableStateOf(false) }
    // 栏底渐显：网格滚进栏下面才把底色淡入（同下载中心/历史记录页）。
    var barProgress by remember { mutableFloatStateOf(0f) }

    val albumsState by pickerState.albumsState.collectAsStateWithLifecycle()
    val mediaState by pickerState.mediaState.collectAsStateWithLifecycle()
    val filteredMediaState by pickerState.filteredMediaState.collectAsStateWithLifecycle()
    val displaySettings by pickerState.displaySettings.collectAsStateWithLifecycle()
    val selectedAlbumId by pickerState.selectedAlbumId.collectAsStateWithLifecycle()

    val hasPermission = permissionState.hasReadPermission
    LaunchedEffect(hasPermission, allowedMedia) {
        if (hasPermission) pickerState.init(allowedMedia)
    }

    // 选中逻辑只有这一份：网格、粘性头组选、全屏预览都走它，
    // 单选模式下「再选一张」必须替换而不是追加。
    val onMediaClick: (Media) -> Unit = { media ->
        val selection = pickerState.selectedMedia
        when {
            selection.contains(media) -> selection.remove(media)
            allowMultiple -> selection.add(media)
            selection.isNotEmpty() -> selection[0] = media
            else -> selection.add(media)
        }
    }

    // 返回键分层：先退搜索 → 再回「全部」→ 再清空选择 → 最后才关掉选择器。
    // 用 PredictiveBackHandler 而不是 BackHandler：手势期间系统会先放预览动画（面板缩小退后），
    // 松手提交时才执行这一层；取消就什么都不做。注册顺序即优先级，Compose 取最后 enabled 的那个。
    PredictiveBackHandler(enabled = isSearching) { events ->
        try {
            events.collect { }
            isSearching = false
        } catch (_: CancellationException) {
            // 手势取消：保持现状
        }
    }
    PredictiveBackHandler(enabled = !isSearching && selectedAlbumId != ALL_ALBUM_ID) { events ->
        try {
            events.collect { }
            pickerState.getAlbum(ALL_ALBUM_ID)
        } catch (_: CancellationException) {
        }
    }

    // 骨架与顶栏都走 Kedge：MD3Exp 下与原来的 MD3 Scaffold + TopAppBar 等价，
    // Miuix 下换成 Miuix Scaffold + Miuix 顶栏（此前整页是 MD3，AGENTS.md 里
    // 「Miuix 分支待补」指的就是这一块）。
    // Dialog 是独立窗口：只把「网格」录成玻璃取样源（栏与按钮在录制层外面，
    // 否则它们会取样到包含自己的那层，RenderThread 递归建节点直接栈溢出崩溃）。
    val contentBackdrop = rememberMochiBackdrop(background = MaterialTheme.colorScheme.background)
    Scaffold(
        modifier = modifier,
        topBar = {
            // 顶栏与相册行共用一块 surfaceContainer（上游是两个嵌套 Scaffold 的
            // topBar，视觉上是一整块），发丝线只画在最下面一行下面。
            val hasAlbums = albumsState.albums.size > 1
            val isRefreshing = mediaState.isLoading && mediaState.media.isNotEmpty()
            // 顶栏和设置页共用 MochiTitleBar；外层玻璃直接取样网格，避免只给按钮套
            // fallback 纯色而看不出通透。标题栏自身的 scroll-edge fade 仍由 barProgress
            // 控制：停在顶部透明，内容滚入栏下方后渐进铺底。
            MochiGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                material = MochiTheme.materials.bar,
                backdrop = contentBackdrop,
                cornerRadius = 0.dp,
            ) {
                Column {
                    MochiTitleBar(
                        title = title ?: stringResource(
                            if (allowMultiple) R.string.media_picker_pick_multiple
                            else R.string.media_picker_pick_single
                        ),
                        titleProgress = { 1f },
                        backgroundProgress = { barProgress },
                        background = MochiTheme.colorScheme.background,
                        backdrop = contentBackdrop,
                        navigationIcon = {
                            MochiBarIconButton(
                                onClick = onDismiss,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.media_picker_close),
                                )
                            }
                        },
                    )
                    if (hasPermission) {
                    MediaAlbumRow(
                        albumsState = albumsState,
                        mediaState = mediaState,
                        selectedAlbumId = selectedAlbumId,
                        onAlbumSelected = pickerState::getAlbum,
                        collapsed = chipsCollapsed,
                    )
                    }
                }
            }
        },
    ) { contentPadding ->
        // 顶部不再让出：网格要能滚到栏底下面，栏的 fade-in 才看得见；
        // 初始位置由网格自己的 top contentPadding 补回来。
        AnimatedContent(
            targetState = hasPermission,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding()),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "mediaPickerPermission",
        ) { permitted ->
            if (permitted) {
                MediaPickerGridWithOverlays(
                    mediaState = mediaState,
                    filteredMediaState = filteredMediaState,
                    albumsState = albumsState,
                    selectedMedia = pickerState.selectedMedia,
                    selectedAlbumId = selectedAlbumId,
                    isSearching = isSearching,
                    allowMultiple = allowMultiple,
                    isManagePermissionAllowed = permissionState.hasManagePermission,
                    onSearchingChange = { isSearching = it },
                    onKeywordChange = { keyword ->
                        pickerState.filterMedia(
                            keyword = keyword.trim(),
                            isForceReset = !isSearching || keyword.isBlank() ||
                                mediaState.media.isEmpty(),
                        )
                    },
                    onPicked = { onPicked(pickerState.selectedMedia.map { it.uri.toUri() }) },
                    onRetry = { pickerState.init(allowedMedia) },
                    onRequestManagePermission = permissionState::requestManagePermission,
                    onRequestFilter = { showFilterSheet = true },
                    onPreview = { uri -> if (uri != null) previewUri = uri },
                    onTakePicture = onTakePicture,
                    onScrolled = { chipsCollapsed = it },
                    onScrollProgress = { barProgress = it },
                    backdrop = contentBackdrop,
                    topContentPadding = contentPadding.calculateTopPadding(),
                    onRefresh = { pickerState.init(allowedMedia) },
                )
            } else {
                PermissionRationale(onRequestPermission = permissionState::requestReadPermission)
            }
        }
    }

    if (showFilterSheet) {
        MediaFilterSheet(
            settings = displaySettings,
            onSettingsChange = pickerState::updateDisplaySettings,
            onDismiss = { showFilterSheet = false },
        )
    }

    // 预览必须喂「过滤后」的列表：搜索时左右翻页不该跳出搜索结果。
    MediaImagePager(
        previewUri = previewUri,
        media = filteredMediaState.media,
        selectedMedia = pickerState.selectedMedia,
        onMediaClick = onMediaClick,
        onDismiss = { previewUri = null },
    )
}

@Composable
private fun PermissionRationale(
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.BrokenImage,
            contentDescription = null,
            modifier = Modifier.size(108.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.media_picker_no_permissions),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        Spacer(Modifier.height(16.dp))
        MochiButton(onClick = onRequestPermission) {
            Text(stringResource(R.string.media_picker_request))
        }
    }
}
