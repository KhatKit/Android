package heizige.kk.khatkit.app.feature.tools.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolIconRepository @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("tool_header_icons", Context.MODE_PRIVATE)
    private val _icons = MutableStateFlow(preferences.all.mapNotNull { (name, icon) ->
        (icon as? String)?.let { name to it }
    }.toMap())
    val icons = _icons.asStateFlow()

    fun rememberHeader(name: String, icon: String) {
        if (_icons.value[name] == icon) return
        preferences.edit().putString(name, icon).apply()
        _icons.value = _icons.value + (name to icon)
    }
}
