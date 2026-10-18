package heizige.kk.khatkit.uikit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeButtonVariant
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.khatkit.ui.UiNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import java.text.NumberFormat

/** One request at a time; taps made during a request remain queued until its acknowledgement. */
internal class CounterButtonState(
    private val scope: CoroutineScope,
    count: Long?,
    localCount: Long,
    private var revision: Long,
) {
    var count by mutableStateOf(count)
        private set
    var localCount by mutableLongStateOf(localCount)
        private set
    var pending by mutableIntStateOf(0)
        private set
    var inFlight by mutableIntStateOf(0)
        private set
    val optimisticCount get() = count?.plus(pending + inFlight)
    val optimisticLocalCount get() = localCount + pending + inFlight
    private var timer: Job? = null
    private var send: (Int) -> Unit = {}

    fun update(count: Long?, localCount: Long, revision: Long, send: (Int) -> Unit) {
        this.send = send
        this.count = count
        this.localCount = localCount
        if (this.revision != revision) {
            this.revision = revision
            inFlight = 0
            schedule()
        }
    }

    fun tap(): Boolean {
        if (pending >= 1000) return false
        pending++
        schedule()
        return true
    }

    private fun schedule() {
        timer?.cancel()
        if (inFlight != 0 || pending == 0) return
        timer = scope.launch {
            delay(if (pending >= 10) 0 else 180)
            flush()
        }
    }

    internal fun flush() {
        if (inFlight != 0 || pending == 0) return
        val batch = minOf(pending, 10)
        pending -= batch
        inFlight = batch
        send(batch)
    }
}

/** Owned above lazy items and pager pages, so recycling a row cannot drop queued taps. */
internal class CounterButtonHost(private val scope: CoroutineScope) {
    private val states = mutableMapOf<Pair<String, String>, CounterButtonState>()
    fun state(node: UiNode): CounterButtonState = states.getOrPut(
        (node.props["id"] as String) to (node.props["action"] as String),
    ) {
        CounterButtonState(scope, (node.props["count"] as? Number)?.toLong(),
            (node.props["localCount"] as? Number)?.toLong() ?: 0,
            (node.props["revision"] as? Number)?.toLong() ?: 0)
    }
}

internal val LocalCounterButtonHost = staticCompositionLocalOf<CounterButtonHost?> { null }

@Composable
internal fun rememberCounterButtonHost(): CounterButtonHost {
    val scope = rememberCoroutineScope()
    return remember(scope) { CounterButtonHost(scope) }
}

@Composable
internal fun CounterButton(
    node: UiNode,
    values: MutableMap<String, Any?>,
    readOnly: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier,
) {
    val host = LocalCounterButtonHost.current ?: rememberCounterButtonHost()
    val state = host.state(node)
    val id = node.props["id"] as String
    val action = node.props["action"] as String
    SideEffect {
        state.update((node.props["count"] as? Number)?.toLong(),
            (node.props["localCount"] as? Number)?.toLong() ?: 0,
            (node.props["revision"] as? Number)?.toLong() ?: 0) { batch ->
            values[id] = batch
            onAction(action)
            // Only a CounterButton action carries the batch. Other actions must
            // not accidentally submit the same clicks for a second time.
            values[id] = 0
        }
    }
    val haptic = LocalHapticFeedback.current
    val particles = remember { mutableStateListOf<Int>() }
    var serial by remember { mutableIntStateOf(0) }
    val style = StyleResolvers.current()
    val orange = Color(0xFFFF875D)
    val ink = Color(0xFF29160E)
    val accent = node.props["tone"] == "primary"
    val colors = if (accent) ButtonDefaults.buttonColors(containerColor = orange, contentColor = ink)
        else ButtonDefaults.filledTonalButtonColors()
    val miuixColors = if (accent) MiuixButtonDefaults.buttonColors(color = orange, contentColor = ink)
        else MiuixButtonDefaults.buttonColors()
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(KedgeColors.surfaceContainerHigh, RoundedCornerShape(20.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KedgeButton(
                onClick = {
                    if (state.tap()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        particles.add(++serial)
                        if (particles.size > 12) particles.removeAt(0)
                    }
                },
                enabled = !readOnly && node.props["disabled"] != true,
                variant = if (accent) KedgeButtonVariant.Primary else KedgeButtonVariant.Secondary,
                colors = colors,
                miuixColors = miuixColors,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(node.props["label"] as? String ?: "+1", style = style.textStyle("body"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val formatted = remember(state.optimisticCount) {
                    state.optimisticCount?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—"
                }
                Row {
                    formatted.forEachIndexed { index, digit ->
                        key(index) {
                            AnimatedContent(
                                targetState = digit,
                                transitionSpec = {
                                    (slideInVertically(tween(160)) { it } + fadeIn(tween(160)))
                                        .togetherWith(slideOutVertically(tween(160)) { -it } + fadeOut(tween(160)))
                                },
                                label = "counter digit",
                            ) {
                                Text(it.toString(), fontSize = 27.sp, fontWeight = FontWeight.SemiBold,
                                    color = KedgeColors.onSurface)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).background(Color(0xFF67BC61), CircleShape))
                    Text(node.props["caption"] as? String ?: "", style = style.textStyle("label"),
                        color = KedgeColors.onSurfaceVariant)
                }
                Text("本机 ${state.optimisticLocalCount} 次", style = style.textStyle("label"),
                    color = KedgeColors.onSurfaceVariant)
                (node.props["hint"] as? String)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = style.textStyle("label"), color = KedgeColors.error)
                }
            }
        }
        particles.toList().forEach { particle ->
            key(particle) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    progress.animateTo(1f, tween(850))
                    particles.remove(particle)
                }
                Text("+1", fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    color = KedgeColors.onSurface,
                    modifier = Modifier.padding(start = 30.dp).graphicsLayer {
                        val p = progress.value
                        translationX = ((particle % 7) * 7 - 28) * density * p
                        translationY = (12 - 90 * p) * density
                        alpha = (1f - p).coerceIn(0f, 1f)
                        rotationZ = ((particle % 5) * 5 - 10) * p
                    })
            }
        }
    }
}
