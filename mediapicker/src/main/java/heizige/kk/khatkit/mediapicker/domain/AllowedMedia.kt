package heizige.kk.khatkit.mediapicker.domain

/**
 * 选择器允许的媒体类型。
 *
 * @param ext 只接受该扩展名（不带点，大小写不敏感）；`null` 表示不按扩展名过滤。
 *   头像等"必须是常规位图"的场景用它收窄到 jpg/png/webp。
 */
sealed class AllowedMedia {
    data class Photos(val ext: String?) : AllowedMedia()
    data object Videos : AllowedMedia()
    data object Both : AllowedMedia()
}
