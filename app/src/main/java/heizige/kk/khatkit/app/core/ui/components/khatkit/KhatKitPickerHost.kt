package heizige.kk.khatkit.app.core.ui.components.khatkit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia
import heizige.kk.khatkit.mediapicker.ui.KhatKitMediaPicker
import heizige.kk.khatkit.uikit.FormPickKind

/**
 * 卡片选择器的宿主挂载点：`mediaPicker.pickMedia` 与表单 `file_picker` / `dir_picker`。
 *
 * - 媒体类请求统一走应用内 [KhatKitMediaPicker]（与聊天附件同一套相册 / 多选 / 预览）；
 * - 目录与文档类请求走系统选择器（[ActivityResultContracts.OpenDocumentTree] /
 *   [ActivityResultContracts.OpenMultipleDocuments]）。
 *
 * 只有在途请求才呈现选择器；交互结果经 provider 回传：媒体复制进缓存后给脚本真实路径，
 * 目录 tree URI 由 [TreeUriPaths] 还原成绝对路径。
 */
@Composable
fun KhatKitPickerHost(provider: KhatKitToolProvider) {
    val mediaPick by provider.mediaPickRequest.collectAsStateWithLifecycle()
    val formPick by provider.formPickRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val directoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val request = formPick ?: return@rememberLauncherForActivityResult
        val path = uri?.let { TreeUriPaths.fromTreeUri(context, it) }
        if (path != null) provider.submitFormPick(request.id, path) else provider.clearFormPick()
    }
    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val request = formPick ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) {
            provider.clearFormPick()
        } else {
            importAndFill(provider, request.id, uris.map { it.toString() })
        }
    }

    // 目录 / 文档类请求没有常驻 UI：请求一出现就拉起系统选择器
    LaunchedEffect(formPick?.id) {
        val request = formPick ?: return@LaunchedEffect
        when {
            request.kind == FormPickKind.DIR -> runCatching { directoryLauncher.launch(null) }
                .onFailure { provider.clearFormPick() }

            !request.preferMediaPicker -> runCatching {
                documentLauncher.launch(mimeTypesOf(request.filter).toTypedArray())
            }.onFailure { provider.clearFormPick() }
        }
    }

    val mediaVisible = mediaPick != null || formPick?.preferMediaPicker == true
    val mediaKind = mediaPick?.options?.kind?.name?.lowercase() ?: formPick?.media.orEmpty()
    KhatKitMediaPicker(
        visible = mediaVisible,
        allowedMedia = allowedMediaOf(mediaKind, formPick?.filter.orEmpty()),
        allowMultiple = mediaPick?.options?.multiple ?: formPick?.multiple ?: false,
        onDismiss = {
            if (mediaPick != null) provider.cancelMediaPick() else provider.clearFormPick()
        },
        onPicked = { uris ->
            val request = formPick
            when {
                request != null -> {
                    provider.clearFormPick()
                    if (uris.isNotEmpty()) importAndFill(provider, request.id, uris.map { it.toString() })
                }

                else -> provider.submitMediaPick(uris.map { it.toString() })
            }
        },
    )
}

/** 复制进缓存再回填表单行；导入失败不动用户已填的值。 */
private fun importAndFill(provider: KhatKitToolProvider, id: String, uris: List<String>) {
    provider.importPickedMedia(uris) { paths ->
        if (paths.isNotEmpty()) provider.submitFormPick(id, paths.joinToString("\n"))
    }
}

/** `media` + `filter` → 选择器允许的类型；filter 是裸扩展名时按该扩展名收窄图片。 */
private fun allowedMediaOf(media: String, filter: String): AllowedMedia = when (media.trim().lowercase()) {
    "video" -> AllowedMedia.Videos
    "any", "all" -> AllowedMedia.Both
    else -> AllowedMedia.Photos(extensionOf(filter))
}

private fun extensionOf(filter: String): String? = filter.trim().lowercase().removePrefix(".")
    .takeIf { it.isNotEmpty() && !it.contains('/') && !it.contains('*') }

/** 系统文档选择器的 mime 过滤；filter 是通配或非 mime 时退回全类型。 */
private fun mimeTypesOf(filter: String): List<String> {
    val raw = filter.trim().lowercase()
    return if (raw.contains('/') && !raw.startsWith("*")) listOf(raw) else listOf("*/*")
}