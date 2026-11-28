package heizige.kk.khatkit.uikit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * custom 组件渲染器注册表（设计文档 7.2 的「逃逸舱」）。
 *
 * 脚本只引用名字（`renderer: "chart.line"`），渲染代码永远在宿主 Kotlin 侧注册，
 * 不进脚本、不可静态注入。
 */
object UiRegistry {

    private val renderers = linkedMapOf<String, @Composable (Map<String, Any?>) -> Unit>()

    fun register(name: String, renderer: @Composable (Map<String, Any?>) -> Unit) {
        renderers[name] = renderer
    }

    fun resolve(name: String?): (@Composable (Map<String, Any?>) -> Unit)? =
        name?.let { renderers[it] }

    fun registeredNames(): Set<String> = renderers.keys.toSet()

    init {
        register("json.pretty") { item ->
            val context = LocalContext.current
            Text(
                text = item.entries.joinToString("\n") { (key, value) -> context.getString(R.string.khatkit_field_value, key, value.toString()) },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp),
            )
        }

        register("keyvalue") { item ->
            Column(modifier = Modifier.padding(4.dp)) {
                item.forEach { (key, value) ->
                    Row {
                        Text(
                            text = stringResource(R.string.khatkit_field_label, key),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = value?.toString().orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
