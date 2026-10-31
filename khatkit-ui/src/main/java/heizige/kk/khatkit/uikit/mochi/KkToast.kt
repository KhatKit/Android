package heizige.kk.khatkit.uikit.mochi

import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    /** 每条提示的序号：同文案连着来两次也要能再放一次（host 的 LaunchedEffect 按它取 key）。 */
    val id: Long = 0L,
)

/**
 * [Toast.showProgress] 返回的句柄：操作结束时调用 [dismiss] 收起那条「进行中」提示。
 * 只有队里当前那条还是它时才收（后来居上的结果提示不会被误收）。
 */
class ToastHandle internal constructor(private val id: Long) {
    fun dismiss() = Toast.dismiss(id)
}

object Toast {
    const val SHORT_DURATION: Long = 2200L

    /** 「进行中」提示的时长：不自动消失，只能靠 [ToastHandle.dismiss] 收起。 */
    const val INFINITE_DURATION: Long = Long.MAX_VALUE

    internal val queue = MutableStateFlow<ToastModel?>(null)
    private val seq = AtomicLong()

    private fun nextId(): Long = seq.incrementAndGet()

    fun show(message: String, isError: Boolean = false) {
        queue.value = ToastModel(message = message, isError = isError, id = nextId())
    }

    fun show(message: String, icon: ImageVector, isError: Boolean = false) {
        queue.value = ToastModel(message = message, isError = isError, iconVector = icon, id = nextId())
    }

    fun show(message: String, painter: Painter, isError: Boolean = false) {
        queue.value = ToastModel(message = message, isError = isError, iconPainter = painter, id = nextId())
    }

    /**
     * 显示一条「进行中」提示（如「保存中…」）：**不会自动消失**，操作结束后调
     * [ToastHandle.dismiss] 收起，再 [show] 结果提示 —— 两者不会叠在一起。
     *
     * 对应上游 96a9bed4d 的 ToasterState.showProgress。
     */
    fun showProgress(message: String, icon: ImageVector? = null): ToastHandle {
        val id = nextId()
        queue.value = ToastModel(
            message = message,
            iconVector = icon,
            durationMillis = INFINITE_DURATION,
            id = id,
        )
        return ToastHandle(id)
    }

    /** 调用方自带 Composable 内容：收起后只剩 message（Mochi 胶囊不支持自定义槽）。 */
    fun showCustom(
        alwaysShow: Boolean = false,
        duration: Long = SHORT_DURATION,
        content: @Composable () -> Unit,
    ) {
        queue.value = ToastModel(message = "", durationMillis = duration, id = nextId())
    }

    fun dismiss() {
        queue.value = null
    }

    /** 按 id 收：只有当前那条还是它时才清队列（结果提示已经顶上来时不要被后到的句柄收掉）。 */
    internal fun dismiss(id: Long) {
        if (queue.value?.id == id) queue.value = null
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
    // 上一条是不是「进行中」提示：只有它需要主动收胶囊（定时提示由 Mochi 自己按 duration 收）。
    var progressShown by remember { mutableStateOf(false) }
    // key 用 id 而不是整条 model：同文案连来两次也能再放一次（原实现靠 show 完立刻 dismiss 达到同样效果）。
    LaunchedEffect(model?.id) {
        val m = model
        if (m == null) {
            if (progressShown) {
                progressShown = false
                state.dismiss()
            }
            return@LaunchedEffect
        }
        progressShown = m.durationMillis == Toast.INFINITE_DURATION
        state.show(
            title = m.message,
            icon = m.iconVector?.let { vec -> { tint: Color -> Icon(vec, contentDescription = null, tint = tint) } },
            durationMillis = m.durationMillis,
        )
        // 定时提示立刻从队列消费掉，让位给下一条；进行中的提示留在队里，等句柄收起
        if (!progressShown) Toast.dismiss(m.id)
    }
    MochiToastHost(state = state, modifier = modifier)
}
