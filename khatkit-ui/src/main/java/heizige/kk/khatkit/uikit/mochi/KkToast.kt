package heizige.kk.khatkit.uikit.mochi

import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.rememberMochiToastState

/**
 * U4f1：替代 Khromia `Toast`。**调用点 API 保持 `Toast.show(...)` 不变**，只换 import。
 *
 * 全局单例状态：[queue] 是唯一队列入口，后到的提示替换当前条（不堆叠）；
 * 由根部的 [KkToastHost] 订阅并转给 Mochi 的 MochiToastHost 渲染。
 */
data class ToastModel(
    val message: String,
    val isError: Boolean = false,
    val iconVector: ImageVector? = null,
    val iconPainter: Painter? = null,
    val durationMillis: Long = Toast.SHORT_DURATION,
)

object Toast {
    const val SHORT_DURATION: Long = 2200L

    internal val queue = MutableStateFlow<ToastModel?>(null)
    private val seq = AtomicLong()

    fun show(message: String, isError: Boolean = false) {
        queue.value = ToastModel(message = message, isError = isError)
    }

    fun show(message: String, icon: ImageVector, isError: Boolean = false) {
        queue.value = ToastModel(message = message, isError = isError, iconVector = icon)
    }

    fun show(message: String, painter: Painter, isError: Boolean = false) {
        queue.value = ToastModel(message = message, isError = isError, iconPainter = painter)
    }

    /** 调用方自带 Composable 内容：收起后只剩 message（Mochi 胶囊不支持自定义槽）。 */
    fun showCustom(
        alwaysShow: Boolean = false,
        duration: Long = SHORT_DURATION,
        content: @Composable () -> Unit,
    ) {
        queue.value = ToastModel(message = "", durationMillis = duration)
    }

    fun dismiss() {
        queue.value = null
    }

    /** 根 host 用：把最新一条转成 Mochi 的 show 调用。 */
    @Composable
    internal fun collectForHost(): ToastModel? {
        val model by queue.collectAsState()
        return model
    }
}

/** 挂在 app 根部（Theme.kt）：订阅 [Toast.queue] 并用 Mochi 胶囊渲染。 */
@Composable
fun KkToastHost(modifier: Modifier = Modifier) {
    val state = rememberMochiToastState()
    val model = Toast.collectForHost()
    LaunchedEffect(model) {
        val m = model ?: return@LaunchedEffect
        state.show(
            title = m.message,
            icon = m.iconVector?.let { vec -> { tint: Color -> Icon(vec, contentDescription = null, tint = tint) } },
            durationMillis = m.durationMillis,
        )
        Toast.dismiss()
    }
    MochiToastHost(state = state, modifier = modifier)
}
