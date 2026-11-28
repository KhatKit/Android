package heizige.kk.khatkit.app.feature.search.data

import android.content.Context
import heizige.kk.khatkit.app.core.ui.hooks.readStringPreference
import heizige.kk.khatkit.app.core.ui.hooks.writeStringPreference
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchSort

/** 搜索页排序偏好：存储 key 与枚举 name 落盘格式保持不变。 */
internal object SearchPreferencesRepository {
    private const val SORT_ORDER_PREF_KEY = "search_page_sort_order"

    fun readSortOrder(context: Context): MessageSearchSort = runCatching {
        MessageSearchSort.valueOf(
            context.readStringPreference(SORT_ORDER_PREF_KEY, MessageSearchSort.RELEVANCE.name)!!
        )
    }.getOrDefault(MessageSearchSort.RELEVANCE)

    fun writeSortOrder(context: Context, sort: MessageSearchSort) {
        context.writeStringPreference(SORT_ORDER_PREF_KEY, sort.name)
    }
}
