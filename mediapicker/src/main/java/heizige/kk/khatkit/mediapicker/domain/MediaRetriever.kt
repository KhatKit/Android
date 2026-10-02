package heizige.kk.khatkit.mediapicker.domain

import kotlinx.coroutines.flow.Flow

/**
 * 媒体仓库抽象。UI 只依赖这个接口，MediaStore 的具体查询放在 `data` 包里。
 *
 * 所有方法都返回 `Result`：MediaStore 在权限被撤回、provider 崩溃、
 * 部分厂商定制列缺失时都会抛异常，交给 UI 显示"重试"而不是直接崩。
 */
interface MediaRetriever {

    fun getAlbumsWithType(allowedMedia: AllowedMedia): Flow<Result<List<Album>>>

    fun mediaFlowWithType(albumId: Long, allowedMedia: AllowedMedia): Flow<Result<List<Media>>>

    fun getMediaByAlbumIdWithType(albumId: Long, allowedMedia: AllowedMedia): Flow<Result<List<Media>>>

    fun getMediaByType(allowedMedia: AllowedMedia): Flow<Result<List<Media>>>
}
