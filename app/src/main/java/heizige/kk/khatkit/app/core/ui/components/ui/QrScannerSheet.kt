package heizige.kk.khatkit.app.core.ui.components.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionCamera
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionStatus
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.icons.photoCamera
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 扫码入口的决策结论。
 *
 * 三态而不是布尔，是因为「还没问」和「问了被拒」必须分开处理：
 * 前者要去弹权限框，后者只能回落，混在一起会导致重复弹框或直接跳过授权。
 */
enum class ScanEntryDecision {
    /** 尚未询问过相机权限，需要发起权限请求 */
    RequestPermission,

    /** 可以打开取景 */
    Scan,

    /** 无法扫描，必须回落到手动粘贴等其它入口 */
    Fallback,
}

/**
 * 纯函数决策：给定相机权限状态与设备相机能力，判断扫码组件应该怎么走。
 *
 * 抽成纯函数是为了能在 JVM 单测里覆盖全部输入组合 —— 真机既没有相机特征可控，
 * 也无法稳定复现「用户拒绝」这一瞬间。
 *
 * 规则：
 * - 无相机硬件（[PackageManager.FEATURE_CAMERA_ANY] 缺失）→ [ScanEntryDecision.Fallback]，
 *   不申请权限（申请了也永远不会 granted，白白多一次系统弹窗）。
 * - 已授权 → [ScanEntryDecision.Scan]。
 * - 从未询问 → [ScanEntryDecision.RequestPermission]。
 * - 已拒绝 / 永久拒绝 → [ScanEntryDecision.Fallback]，由调用方回落到手动粘贴。
 *
 * @param permissionStatus 当前相机权限状态；`null` 表示尚未探测到，按「未询问」处理。
 * @param hasCameraHardware 设备是否具备任意相机硬件。
 */
fun decideScanEntry(
    permissionStatus: PermissionStatus?,
    hasCameraHardware: Boolean,
): ScanEntryDecision {
    if (!hasCameraHardware) return ScanEntryDecision.Fallback
    return when (permissionStatus) {
        PermissionStatus.Granted -> ScanEntryDecision.Scan
        PermissionStatus.NotRequested, null -> ScanEntryDecision.RequestPermission
        PermissionStatus.Denied, PermissionStatus.DeniedPermanently -> ScanEntryDecision.Fallback
    }
}

/**
 * 扫码结果的一次性闸门。
 *
 * 取景是持续流，同一个二维码会连续命中几十帧；调用方只应被回调一次，
 * 否则会连续弹「是否导入群聊」。这里用 [AtomicBoolean] 而不是 Compose 状态，
 * 因为回调发生在 CameraX 的分析线程上，UI 状态在那个时刻写入并不安全，
 * 且重组无法覆盖跨线程的一次性语义。
 */
class ScanGate {
    private val accepted = AtomicBoolean(false)

    /** 是否已经放行过一次结果 */
    val isAccepted: Boolean get() = accepted.get()

    /**
     * 尝试放行一次结果。
     *
     * @return `true` 表示本次调用赢得了唯一的一次放行名额，调用方应当真正回调；
     *   `false` 表示名额已被消耗，本次应当静默丢弃。
     */
    fun tryAccept(): Boolean = accepted.compareAndSet(false, true)

    /** 复位闸门，便于组件复用或单测复用 */
    fun reset() {
        accepted.set(false)
    }
}

/**
 * 判断一帧是否值得解码：只有「时间戳有效且比上一帧更新」时才返回 `true`。
 *
 * 存在的意义是丢掉乱序到达的旧 buffer —— 旋转或切换用例时可能收到时间戳更早的帧，
 * 直接解码会解出上一帧的内容。
 *
 * ⚠️ **时间戳为 0（或负数）时必须放行**：[ImageProxy.getImageInfo] 的时间戳来自底层
 * `android.media.Image.getTimestamp()`（camera-camera2 的 `AndroidImage` 只是原样透传），
 * 平台并不保证它一定被赋值。CameraX 自己的
 * `ImageAnalysisAbstractAnalyzer` 也只是把这个值搬进新的 `ImageInfo`，不做补齐。
 * 一旦某后端恒返回 0，「0 <= 0」会把**每一帧**都判成过期，取景直接扫不出任何东西 ——
 * 这是比丢帧严重得多的故障，所以这里显式把「无效时间戳」当作「不做乱序判定」。
 *
 * @param lastAnalyzedTimestamp 上一帧已接受的时间戳；初始为 `0`。
 * @param frameTimestamp 本帧的时间戳。
 */
fun shouldAnalyzeFrame(lastAnalyzedTimestamp: Long, frameTimestamp: Long): Boolean {
    if (frameTimestamp <= 0L) return true
    return frameTimestamp > lastAnalyzedTimestamp
}

/**
 * 构造扫码用的 MLKit scanner：只开 `QR_CODE` 一种格式。
 *
 * 从 [QrScanSession] 的字段初始化里**原样提取**，只为给 androidTest 一个与生产逐字同款的
 * scanner 入口 —— 测试必须用这条路径建 scanner，才能断言真实的 `mediaImage -> MLKit` 解码链路，
 * 而不是另起一套 options。
 */
internal fun buildQrScanner(): BarcodeScanner =
    BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

/**
 * 把一帧 [ImageProxy] 交给 MLKit 解码，返回异步任务。
 *
 * 这是相机扫码链路里**唯一**把底层 `android.media.Image`（[ImageProxy.getImage]）转成 MLKit
 * [InputImage] 的地方：`InputImage.fromMediaImage(mediaImage, imageInfo.rotationDegrees)`。
 * 抽成 `internal` 顶层函数是为了让 androidTest 能用真实 `ImageProxy`（ImageReader 合成帧）直接
 * 调用，从而覆盖此前只跑过 `InputImage.fromBitmap`、从未在 `mediaImage` 这一层跑通的路径。
 *
 * `proxy.image` 为 null（非 [android.media.Image] 支撑的代理）时返回 `null`，与 [analyzeFrame]
 * 的早退语义一致。
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
internal fun processQrFrame(
    image: ImageProxy,
    scanner: BarcodeScanner,
): Task<List<Barcode>>? {
    val mediaImage = image.image ?: return null
    return scanner.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
}

/**
 * 从一帧解出的全部 barcode 里取**第一个**有 `rawValue` 的字符串。
 *
 * 从 [QrScanSession] 的 success 回调里**原样提取**（`firstNotNullOfOrNull { it.rawValue }`）：
 * 一帧出现多个二维码时生产语义就是「只回调第一个」，抽出来才能在 androidTest 里钉住。
 */
internal fun firstQrValue(barcodes: List<Barcode>): String? =
    barcodes.firstNotNullOfOrNull { it.rawValue }

/**
 * 相机扫码弹层：用 CameraX 取景 + MLKit Barcode Scanning 扫二维码。
 *
 * 选型说明（为什么是这两个库而不是手写）：
 * - 取景与生命周期绑定直接用 CameraX 的 `Preview` + `ImageAnalysis` + `ProcessCameraProvider`，
 *   手写 camera2 意味着自己处理 SurfaceTexture、矩阵变换、SENSOR_ORIENTATION 与
 *   前后台重绑，代码量和出错面都大得多，且 CameraX 已经把这些坑填好了。
 * - 解码用 MLKit 的 bundled `barcode-scanning`：模型随 APK 打包，不依赖 Google Play Services，
 *   离线设备（含无 GMS 的国产 ROM）也能扫。
 *
 * 关键取舍：
 * - **只扫 QR_CODE**：群聊导入只认二维码链接。MLKit 默认会跑全格式检测，
 *   限定格式后每帧的检测区域大幅收窄，取景帧率明显更跟手。
 * - **STRATEGY_KEEP_ONLY_LATEST**：图像分析是「只看当下」的场景，旧帧没有回放价值。
 *   保持相机最新一帧、丢掉积压帧，才能让预览延迟稳定在可接受范围内；
 *   若用 KEEP_ONLY_LATEST 之外会积压的策略，内存会被拖高且回显越来越迟。
 * - **每帧必关 [ImageProxy]**：`ImageProxy` 背后的 buffer 数量有限，漏关一次就会
 *   丢帧甚至耗尽图像流。这里在 `finally` 里兜底（含异常路径），异步解码分支则
 *   交给 MLKit 的 complete 回调关闭，避免还在读就被关掉。
 * - **fallback 是硬要求**：相机权限被拒、或设备根本没有相机时，扫码无法进行。
 *   此时组件不崩溃、不空转，而是回调 `onResult(null)`，由调用方回落到「手动粘贴」。
 *   群聊导入必须保留粘贴入口，否则弱权限设备上功能直接不可用。
 *
 * @param onResult 扫描结果。`onResult(内容)` 表示扫到二维码；`onResult(null)` 表示
 *   未扫到（权限被拒 / 无相机硬件 / 用户取消），调用方应回落到手动粘贴等其它入口。
 *   无论成功、失败还是取消，本回调最多被调用一次。
 * @param onDismiss 关闭弹层。调用方需自行从 Composition 中移除本组件。
 */
@Composable
fun QrScannerSheet(
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // onResult 由调用方传入，身份每次重组都可能变；用 rememberUpdatedState 兜住，
    // 避免取景会话记住旧闭包。
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val gate = remember { ScanGate() }

    val cameraPermission = rememberPermissionState(setOf(PermissionCamera))
    PermissionManager(permissionState = cameraPermission)

    val hasCameraHardware = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val permissionStatus = cameraPermission.permissionStates[Manifest.permission.CAMERA]
    val decision = decideScanEntry(permissionStatus, hasCameraHardware)

    // 权限状态变化会带动本副作用重跑：首次请求权限、拒绝后回落。
    LaunchedEffect(decision) {
        when (decision) {
            ScanEntryDecision.RequestPermission -> cameraPermission.requestPermissions()
            ScanEntryDecision.Fallback -> if (gate.tryAccept()) currentOnResult(null)
            ScanEntryDecision.Scan -> Unit // 取景交给下面的 AndroidView 与会话副作用
        }
    }

    // 仅在真正进入取景时才建会话；decision 离开 Scan 时旧会话被 onDispose 释放。
    val session = remember(decision) {
        if (decision == ScanEntryDecision.Scan) {
            QrScanSession(
                onBarcode = { raw ->
                    if (gate.tryAccept()) currentOnResult(raw)
                },
                // 绑定失败与「无相机 / 权限被拒」走同一条回落路径：回调 onResult(null)，
                // 否则弹层会永远停在黑屏取景框，调用方也永远等不到回调。
                onBindFailed = {
                    if (gate.tryAccept()) currentOnResult(null)
                },
            )
        } else {
            null
        }
    }
    DisposableEffect(session) {
        onDispose { session?.release() }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    LaunchedEffect(session, previewView) {
        val activeSession = session ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect
        activeSession.bind(context, lifecycleOwner, view)
    }

    PrimaryBottomSheet(
        visible = true,
        title = "扫码导入",
        imageVector = photoCamera,
        dismissText = "取消",
        onDismiss = {
            // 用户取消同样走 onResult(null)，与权限拒绝、无相机保持同一条回落路径。
            if (gate.tryAccept()) currentOnResult(null)
            currentOnDismiss()
        },
        scrollable = false,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (decision == ScanEntryDecision.Scan) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            PreviewView(viewContext).apply { previewView = this }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    // 取景框只是视觉引导；真正裁剪检索区域的是 MLKit 内部检测器，
                    // 这里不额外做区域裁剪 —— 移动端每帧裁剪的收益抵不过实现复杂度。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .background(Color.Transparent),
                    )
                }
                Text(
                    text = "将二维码放入框内即可自动识别",
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Text(
                    text = "当前无法使用相机扫码，请改用手动粘贴",
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 一次取景会话：持有 MLKit scanner、分析线程与 CameraX 绑定句柄。
 *
 * 生命周期与 QrScannerSheet 的取景分支一一对应，靠 [release] 收尾。
 * 之所以做成独立类而不是一堆 Composable 局部状态，是因为 scanner 与线程池都是
 * 「需要显式关闭的资源」，把它们关在可被 Compose 精确控制的确定性作用域里最省心。
 */
private class QrScanSession(
    private val onBarcode: (String) -> Unit,
    private val onBindFailed: () -> Unit,
) {
    private companion object {
        const val TAG = "QrScannerSheet"
    }

    // 单线程即可：分析是串行的，多线程只会让 MLKit 任务排队并放大内存占用。
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val scanner = buildQrScanner()

    private var cameraProvider: ProcessCameraProvider? = null

    // 只做基本单调性检查：KEEP_ONLY_LATEST 下仍可能收到时间戳更旧的 buffer，
    // 直接解码会在旋转/切换时解出上一帧的内容。时间戳不可用时该检查自动失效，
    // 判定逻辑见 [shouldAnalyzeFrame]。
    @Volatile
    private var lastAnalyzedTimestamp = 0L

    private val analyzer = ImageAnalysis.Analyzer { proxy ->
        // 必须兜住所有异常：analyzer 跑在 analysisExecutor 上，而 CameraX 的
        // ImageAnalysisAbstractAnalyzer.lambda$analyzeImage$0 是**裸调** Analyzer.analyze
        // 的（反编译 1.6.2 字节码确认：没有 try/catch），异常会一路逃到本线程的
        // UncaughtExceptionHandler —— 默认实现直接杀进程。
        // analyzeFrame 内部已用 finally 保证关闭 proxy，这里再兜一层只针对
        // 「关闭动作本身之前就崩了」的极端情况，close 幂等所以重复调用无害。
        runCatching { analyzeFrame(proxy) }
            .onFailure { error ->
                runCatching { proxy.close() }
                Log.w(TAG, "单帧分析异常，已丢弃该帧", error)
            }
    }

    /**
     * 绑定相机到 [lifecycleOwner] 与 [previewView]。
     *
     * 可重复调用（AndroidView 重建时 [QrScannerSheet] 会再触发一次），内部先
     * `unbindAll()` 再重绑，因此不会叠加用例。
     *
     * 绑定失败（相机被其它应用占用、设备只有前置摄像头、provider 初始化失败等）
     * 一律不抛给调用方，而是回调 [onBindFailed] 让上层回落到手动粘贴。
     */
    fun bind(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // 绝不能让异常冒到主线程 Looper（那会崩 App）：统一吞掉并通知上层回落。
            runCatching {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                // 必须先解绑：对同一个 lifecycle 重复绑定同一组用例会抛
                // IllegalArgumentException，而 AndroidView 重建会重复触发 bind。
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure { error ->
                Log.w(TAG, "绑定相机用例失败，回落到手动粘贴", error)
                // 这里不自行 release：会话仍由 Compose 的 DisposableEffect 统一收尾，
                // 避免 release 后又被重复 bind 撞上已 shutdown 的线程池。
                onBindFailed()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 单帧分析。**必须保证 [proxy] 一定被关闭**。
     *
     * 异步解码路径把关闭动作交给 MLKit 的 complete 回调（此时 buffer 才真正读完）；
     * 同步退出路径（空帧、过期帧、抛异常）由 finally 兜底关闭。
     *
     * `ImageProxy.getImage()` 在 CameraX 里标注了 `@ExperimentalGetImage`：拿到的是
     * 底层 `android.media.Image`，跨进程传递需要自己保证格式与生命周期。这里只在本方法内
     * 读取并立刻交给 MLKit，不把 image 存到字段或跨帧复用，所以显式 opt-in 是安全的。
     *
     * 这里必须用 `androidx.annotation.OptIn` 而不是 Kotlin 的 `kotlin.OptIn`：CameraX 的
     * `@ExperimentalGetImage` 是 Java 侧标记，Android Lint 的 UnsafeOptInUsageError 只认前者。
     */
    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    private fun analyzeFrame(proxy: ImageProxy) {
        var closeInFinally = true
        try {
            val mediaImage = proxy.image
            if (mediaImage == null) return

            val imageInfo = proxy.imageInfo
            if (!shouldAnalyzeFrame(lastAnalyzedTimestamp, imageInfo.timestamp)) return
            if (imageInfo.timestamp > 0L) lastAnalyzedTimestamp = imageInfo.timestamp

            val task = processQrFrame(proxy, scanner) ?: return
            task
                .addOnSuccessListener { barcodes ->
                    val raw = firstQrValue(barcodes) ?: return@addOnSuccessListener
                    onBarcode(raw)
                }
                .addOnFailureListener {
                    // 单帧解码失败（运动模糊、遮挡）不值得中断取景，丢弃本帧继续下一帧。
                }
                .addOnCompleteListener { proxy.close() }
            closeInFinally = false
        } finally {
            if (closeInFinally) proxy.close()
        }
    }

    /** 释放全部资源：解绑用例、关闭 scanner、回收线程池 */
    fun release() {
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        runCatching { scanner.close() }
        analysisExecutor.shutdown()
    }
}