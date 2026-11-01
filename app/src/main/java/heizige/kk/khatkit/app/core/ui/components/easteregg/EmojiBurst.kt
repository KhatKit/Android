package heizige.kk.khatkit.app.core.ui.components.easteregg

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 一次 emoji 爆发的形状参数。
 *
 * 速度是 px/s、方向是度（屏幕坐标：0 = 右、90 = 下），emoji 字号按 sp 走并跟随系统字号。
 */
@Immutable
data class EmojiBurstSpec(
    /** 粒子总数。 */
    val count: Int = 12,
    /** 初速下限 / 上限（px/s）。 */
    val speedMin: Float = 320f,
    val speedMax: Float = 640f,
    /** 抛出方向的扇形范围（度）。默认朝上 ±65°。 */
    val angleMinDeg: Float = 205f,
    val angleMaxDeg: Float = 335f,
    /** 字号范围（sp）。大小混着来才有「撒了一把」的层次，统一字号看着像贴图。 */
    val sizeMinSp: Float = 20f,
    val sizeMaxSp: Float = 32f,
    /** 出生点周围的抖动半径（dp）：让同一帧出生的一批不叠在一个点上。 */
    val spreadDp: Float = 7f,
    /** 粒子寿命（秒），到点且落地减速后才回收。 */
    val lifetimeSeconds: Float = 3.8f,
)

/**
 * 彩蛋粒子引擎的句柄：页面拿着它按任意子元素的坐标撒 emoji。
 *
 * 早先的 API 是 content(onBurst: (Offset) -> Unit)，坐标由页面自己用 positionInParent 算 ——
 * 那是相对图标父容器的偏移，而粒子画在 host 的坐标系里，于是每次爆炸都从左上角冒出来，
 * 跟手指点的位置没关系。现在 [burst] 收的是 [LayoutCoordinates]（图标自己那层），
 * 由 host 用 localPositionOf 换算到自己的坐标系，绝对不跑偏。
 */
@Stable
class EmojiBurstState {
    internal var host: LayoutCoordinates? = null
    internal var hostSize: Size = Size.Zero
    internal var density: Density = Density(1f, 1f)
    internal val requests = mutableStateListOf<BurstRequest>()

    /** 在 [anchor] 的中心炸一发；[anchor] 还没布局好时退化成 host 顶部居中。 */
    fun burst(anchor: LayoutCoordinates?, spec: EmojiBurstSpec = EmojiBurstSpec()) {
        val root = host ?: return
        val center = if (anchor != null && anchor.isAttached) {
            root.localPositionOf(anchor, Offset(anchor.size.width / 2f, anchor.size.height / 2f))
        } else {
            Offset(hostSize.width / 2f, hostSize.height * 0.3f)
        }
        requests.add(BurstRequest(center = center, spec = spec, remaining = spec.count, rain = false))
    }

    /** 从页面顶端往下撒（长按蓄力满之后的「大彩蛋」）。 */
    fun rain(spec: EmojiBurstSpec = EmojiBurstSpec(count = 18, lifetimeSeconds = 4.2f)) {
        requests.add(BurstRequest(center = Offset.Zero, spec = spec, remaining = spec.count, rain = true))
    }
}

/** 记一个粒子引擎，跟着页面/宿主活下去。 */
@Composable
fun rememberEmojiBurstState(): EmojiBurstState = remember { EmojiBurstState() }

/**
 * 粒子宿主：铺满整页（或一个区域），把 [state] 收到的爆发画在上面，下面挂页面内容。
 *
 * 物理是 Verlet 积分 + 圆-圆碰撞（emoji 之间互相弹开）+ 四壁反弹 + 地面摩擦，
 * 落地减速到阈值以下才回收，所以 emoji 会「躺」一会儿再消失，不会空中一散就没。
 */
@Composable
fun EmojiBurstHost(
    state: EmojiBurstState,
    emojiOptions: List<String>,
    modifier: Modifier = Modifier,
    maxParticles: Int = 160,
    content: @Composable () -> Unit,
) {
    val particles = remember { mutableStateListOf<EmojiParticle>() }
    // 自增 id 放在普通数组里：用 mutableStateOf 的话每放一个粒子就触发一次重组合
    val nextId = remember { longArrayOf(0L) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coordinates ->
            state.host = coordinates
        },
    ) {
        state.hostSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        state.density = density

        content()

        particles.forEach { particle ->
            key(particle.id) {
                Text(
                    text = particle.emoji,
                    style = TextStyle(fontSize = particle.sizeSp.sp),
                    modifier = Modifier.offset {
                        IntOffset(
                            (particle.position.x - particle.radiusPx).roundToInt(),
                            (particle.position.y - particle.radiusPx).roundToInt(),
                        )
                    },
                )
            }
        }

        LaunchedEffect(density, emojiOptions) {
            if (emojiOptions.isEmpty()) return@LaunchedEffect
            val gravity = 2400f
            val wallDamping = 0.7f
            val floorDamping = 0.62f
            val restitution = 0.72f
            val damping = 0.99f
            val spawnPerFrame = 4
            var lastTime = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastTime = now

                // 一帧只放几个，一整发炸开是「涌出来」而不是「一坨同时出现」
                if (state.requests.isNotEmpty()) {
                    val iterator = state.requests.listIterator()
                    while (iterator.hasNext()) {
                        val request = iterator.next()
                        val spawnCount = minOf(spawnPerFrame, request.remaining)
                        repeat(spawnCount) {
                            val particle = spawn(request, state, emojiOptions, nextId[0]++) ?: return@repeat
                            if (particles.size >= maxParticles) particles.removeAt(0)
                            particles.add(particle)
                        }
                        val remaining = request.remaining - spawnCount
                        if (remaining <= 0) iterator.remove() else iterator.set(request.copy(remaining = remaining))
                    }
                }
                if (particles.isEmpty()) continue

                val width = state.hostSize.width
                val height = state.hostSize.height
                if (width <= 0f || height <= 0f) continue

                val count = particles.size
                val current = ArrayList<EmojiParticle>(count)
                for (i in 0 until count) current.add(particles[i])

                val positions = Array(count) { current[it].position }
                val previousPositions = Array(count) { current[it].previousPosition }
                val ages = FloatArray(count) { current[it].ageSeconds + dt }
                val radii = FloatArray(count) { current[it].radiusPx }

                for (i in 0 until count) {
                    val position = positions[i]
                    val previous = previousPositions[i]
                    val vx = (position.x - previous.x) * damping
                    val vy = (position.y - previous.y) * damping
                    previousPositions[i] = position
                    positions[i] = Offset(position.x + vx, position.y + vy + gravity * dt * dt)
                }

                // 两遍松弛：一遍够弹开，两遍看起来才不像叠在一起
                repeat(2) {
                    for (i in 0 until count) {
                        for (j in i + 1 until count) {
                            val dx = positions[j].x - positions[i].x
                            val dy = positions[j].y - positions[i].y
                            val minDistance = radii[i] + radii[j]
                            val distSq = dx * dx + dy * dy
                            if (distSq >= minDistance * minDistance) continue
                            val dist = sqrt(distSq).coerceAtLeast(0.01f)
                            val nx = dx / dist
                            val ny = dy / dist
                            val overlap = minDistance - dist
                            positions[i] = Offset(positions[i].x - nx * overlap / 2f, positions[i].y - ny * overlap / 2f)
                            positions[j] = Offset(positions[j].x + nx * overlap / 2f, positions[j].y + ny * overlap / 2f)
                            val vi = positions[i] - previousPositions[i]
                            val vj = positions[j] - previousPositions[j]
                            val rvx = vj.x - vi.x
                            val rvy = vj.y - vi.y
                            val velAlongNormal = rvx * nx + rvy * ny
                            if (velAlongNormal < 0f) {
                                val impulse = -(1f + restitution) * velAlongNormal / 2f
                                val ix = impulse * nx
                                val iy = impulse * ny
                                val newVi = Offset(vi.x - ix, vi.y - iy)
                                val newVj = Offset(vj.x + ix, vj.y + iy)
                                previousPositions[i] = positions[i] - newVi
                                previousPositions[j] = positions[j] - newVj
                            }
                        }
                    }
                    for (i in 0 until count) {
                        val r = radii[i]
                        positions[i] = Offset(
                            positions[i].x.coerceIn(r, (width - r).coerceAtLeast(r)),
                            positions[i].y.coerceIn(r, (height - r).coerceAtLeast(r)),
                        )
                    }
                }

                // 贴墙 / 落地时把法向速度吃掉一部分，否则粒子会一直抖
                for (i in 0 until count) {
                    val r = radii[i]
                    val position = positions[i]
                    val previous = previousPositions[i]
                    var vx = position.x - previous.x
                    var vy = position.y - previous.y
                    if (position.x <= r && vx < 0f) {
                        vx = -vx * wallDamping
                    } else if (position.x >= width - r && vx > 0f) {
                        vx = -vx * wallDamping
                    }
                    if (position.y <= r && vy < 0f) {
                        vy = -vy * wallDamping
                    } else if (position.y >= height - r && vy > 0f) {
                        vy = -vy * floorDamping
                    }
                    previousPositions[i] = Offset(position.x - vx, position.y - vy)
                }

                val next = ArrayList<EmojiParticle>(count)
                for (i in 0 until count) {
                    val position = positions[i]
                    val previous = previousPositions[i]
                    val age = ages[i]
                    val vx = (position.x - previous.x) / dt
                    val vy = (position.y - previous.y) / dt
                    val speed = sqrt(vx * vx + vy * vy)
                    val settled = position.y >= height - radii[i] - 0.5f && speed < 20f
                    if (settled) previousPositions[i] = position
                    val expired = age >= current[i].spec.lifetimeSeconds && settled && speed < 200f
                    if (!expired) {
                        next.add(
                            current[i].copy(
                                position = position,
                                previousPosition = previousPositions[i],
                                ageSeconds = age,
                            )
                        )
                    }
                }
                particles.clear()
                particles.addAll(next)
            }
        }
    }
}

/** 按一次爆发请求造一个粒子；出生点随机抖动，速度按扇形散布。 */
private fun spawn(
    request: BurstRequest,
    state: EmojiBurstState,
    options: List<String>,
    id: Long,
): EmojiParticle? {
    val density = state.density
    val width = state.hostSize.width
    val height = state.hostSize.height
    if (width <= 0f || height <= 0f) return null
    val spec = request.spec
    val sizeSp = spec.sizeMinSp + Random.nextFloat() * (spec.sizeMaxSp - spec.sizeMinSp)
    val radiusPx = with(density) { (sizeSp / 2f).sp.toPx() }

    val origin = if (request.rain) {
        Offset(
            x = Random.nextFloat() * width,
            y = -radiusPx * 2f,
        )
    } else {
        val spread = with(density) { spec.spreadDp.dp.toPx() }
        val angle = Math.toRadians(Random.nextDouble(0.0, 360.0)).toFloat()
        val distance = spread * Random.nextFloat()
        Offset(
            x = request.center.x + cos(angle) * distance,
            y = request.center.y + sin(angle) * distance,
        )
    }

    val angleRad = if (request.rain) {
        // 雨：主要往下，左右给一点偏
        Math.toRadians(Random.nextDouble(60.0, 120.0)).toFloat()
    } else {
        Math.toRadians(Random.nextDouble(spec.angleMinDeg.toDouble(), spec.angleMaxDeg.toDouble())).toFloat()
    }
    val speed = spec.speedMin + Random.nextFloat() * (spec.speedMax - spec.speedMin)
    val vx = cos(angleRad) * speed
    val vy = sin(angleRad) * speed

    val position = Offset(
        origin.x.coerceIn(radiusPx, (width - radiusPx).coerceAtLeast(radiusPx)),
        origin.y.coerceIn(-radiusPx * 2f, (height - radiusPx).coerceAtLeast(radiusPx)),
    )
    return EmojiParticle(
        id = id,
        emoji = options[Random.nextInt(options.size)],
        position = position,
        previousPosition = Offset(position.x - vx / 60f, position.y - vy / 60f),
        ageSeconds = 0f,
        radiusPx = radiusPx,
        sizeSp = sizeSp,
        spec = spec,
    )
}

internal data class EmojiParticle(
    val id: Long,
    val emoji: String,
    val position: Offset,
    val previousPosition: Offset,
    val ageSeconds: Float,
    val radiusPx: Float,
    val sizeSp: Float,
    val spec: EmojiBurstSpec,
)

internal data class BurstRequest(
    val center: Offset,
    val spec: EmojiBurstSpec,
    val remaining: Int,
    val rain: Boolean,
)
