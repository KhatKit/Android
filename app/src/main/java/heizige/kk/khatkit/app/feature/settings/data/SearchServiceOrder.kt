package heizige.kk.khatkit.app.feature.settings.data
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.search.SearchOptions
import heizige.kk.khatkit.app.feature.settings.SettingViewModel

/**
 * 换掉搜索服务列表，并让**当前使用的搜索服务**继续指着同一条服务。
 *
 * `Settings.searchServiceSelected` 存的是**下标**（`SearchPicker` 与 `SearchTools` 都按下标取），
 * 所以新增（插在队首）、删除、拖动排序只要动了列表顺序，下标就会指向另一条服务 ——
 * 表现为「加完一条新服务，聊天里用的搜索源就悄悄变了」。这里按**服务 id**重新绑定：
 * 选中项在换表前后是同一条服务；那条服务不在了（被删）就退回合法范围内的老下标。
 */
internal fun Settings.replacingSearchServices(newServices: List<SearchOptions>): Settings {
    val pinnedId = searchServices.getOrNull(searchServiceSelected)?.id
    val pinnedIndex = newServices.indexOfFirst { it.id == pinnedId }
    val selected = if (pinnedIndex >= 0) {
        pinnedIndex
    } else {
        searchServiceSelected.coerceIn(0, (newServices.size - 1).coerceAtLeast(0))
    }
    return copy(searchServices = newServices, searchServiceSelected = selected)
}

/** 新增一条搜索服务（插在队首，与页面里「最后加的排最前」一致），选中项不跟着漂。 */
internal fun addSearchRepository(
    vm: SettingViewModel,
    settings: Settings,
    service: SearchOptions,
) {
    vm.updateSettings(
        settings.replacingSearchServices(listOf(service) + settings.searchServices)
    )
}

/**
 * 按**条目 key**（服务 id）换算下标后移动：解析不出目标（拖到了搜索框、分组卡这类非条目上）
 * 就原地不动 —— 那些位置本来就不是服务。
 */
internal fun moveSearchRepository(
    vm: SettingViewModel,
    settings: Settings,
    fromServiceId: Any,
    toServiceId: Any,
) {
    val fromIndex = settings.searchServices.indexOfFirst { it.id == fromServiceId }
    val toIndex = settings.searchServices.indexOfFirst { it.id == toServiceId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return
    val newServices = settings.searchServices.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
    vm.updateSettings(settings.replacingSearchServices(newServices))
}

/** 删掉一个搜索服务。只剩一个时不动 —— 至少要留一个可用的搜索源。 */
internal fun removeSearchRepository(
    vm: SettingViewModel,
    settings: Settings,
    service: SearchOptions,
) {
    if (settings.searchServices.size <= 1) return
    val newServices = settings.searchServices.toMutableList().apply { remove(service) }
    vm.updateSettings(settings.replacingSearchServices(newServices))
}
