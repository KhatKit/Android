package heizige.kk.khatkit.app.core.ui.components.khatkit

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore

/**
 * 把系统目录选择器（`ACTION_OPEN_DOCUMENT_TREE`）返回的 tree URI 还原成真实文件路径。
 *
 * 卡片脚本拿到的是路径而不是 URI（`fs` / `tool` 全按路径工作），所以必须在宿主侧
 * 把 `volume:relative/path` 形式的 document id 拼回 `/storage/...` 绝对路径。
 */
object TreeUriPaths {

    /** 主存储（手机内置）根目录。 */
    const val PRIMARY_ROOT = "/storage/emulated/0"

    /**
     * `volume` + `relativePath` → 绝对路径；卷未知时返回 null。
     *
     * @param roots 卷名 → 卷根目录（见 [volumeRoots]）；`primary` 不在其中也能解析。
     */
    fun resolve(volume: String, relativePath: String, roots: Map<String, String>): String? {
        val root = if (volume.equals("primary", ignoreCase = true)) {
            PRIMARY_ROOT
        } else {
            roots[volume]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        } ?: return null
        val relative = relativePath.trim().trim('/')
        return if (relative.isEmpty()) root else "$root/$relative"
    }

    /** tree URI → 绝对路径；URI 不是目录授权或解析失败时返回 null。 */
    fun fromTreeUri(context: Context, uri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        if (volume.isEmpty()) return null
        return resolve(volume, documentId.substringAfter(':', ""), volumeRoots(context))
    }

    /** 卷名 → 卷根目录；取不到（未挂载 / API < 29）时不出现。 */
    fun volumeRoots(context: Context): Map<String, String> = buildMap {
        put("primary", PRIMARY_ROOT)
        // 次级卷枚举 API 29 才有；更低版本只解析主存储
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@buildMap
        runCatching {
            for (name in MediaStore.getExternalVolumeNames(context)) {
                val dir = context.getExternalFilesDirs(name).firstOrNull()?.absolutePath ?: continue
                val root = dir.substringBefore("/Android/data").trimEnd('/')
                if (root.isNotEmpty()) put(name, root)
            }
        }
    }
}