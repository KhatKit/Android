package heizige.kk.khatkit.app.core.data.model

import heizige.kk.khatkit.app.R

internal enum class ToolCategory(val title: String, val icon: String, @androidx.annotation.StringRes val titleRes: Int) {
    IMAGE("图片处理", "image", R.string.tools_category_image),
    MEDIA("影音阅读", "play-circle", R.string.tools_category_media),
    TEXT("文字办公", "document-text", R.string.tools_category_text),
    FILE("文件管理", "folder", R.string.tools_category_file),
    WEB("网络工具", "globe", R.string.tools_category_web),
    DEVICE("设备管理", "phone-portrait", R.string.tools_category_device),
    LIFE("生活服务", "cafe", R.string.tools_category_life),
    DEVELOPMENT("开发工具", "code-slash", R.string.tools_category_development),
    OTHER("其他工具", "apps", R.string.tools_category_other),
}
