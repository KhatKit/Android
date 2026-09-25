package heizige.kk.khatkit.bridge

import android.view.View
import android.view.WindowManager

/**
 * 七个 bridge（设计文档第 7 节）。全部由宿主 Kotlin 实现，脚本只能调用暴露出来的方法。
 *
 * 权限模型（审核制下，这是能力声明而非运行时沙箱）：
 *   tool          L0 —— 文件 / 网络 / 媒体 / 压缩，无需特殊授权
 *   ui            L0 —— 声明式表单，宿主 Compose 渲染并阻塞回传
 *   download      L0 —— 后台下载服务，脚本退出后任务继续
 *   store         L0 —— 按卡片隔离的持久化（kv / file / db / secret）
 *   shizuku       L1 —— shell 能力（安卓），桌面无对应物则自动隐藏
 *   accessibility L1 —— 无障碍自动化（安卓），需用户在系统设置开启
 *   root          L2 —— su 能力（安卓），桌面映射为 sudo 且默认禁用
 */
interface ToolBridge {
    fun readText(path: String): String
    fun writeText(path: String, content: String)
    fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String
    fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): String

    /**
     * multipart/form-data POST（极客猫等表单接口）。
     *
     * fileField 与 filePath 都非空时附带单个文件段；saveBinary=true 且响应
     * Content-Type 为图片时，二进制写入共享存储并返回路径，否则返回响应文本。
     */
    fun httpMultipart(
        url: String,
        fields: Map<String, String>,
        fileField: String? = null,
        filePath: String? = null,
        headers: Map<String, String> = emptyMap(),
        saveBinary: Boolean = true,
    ): String

    /** 读取本地文件为 data URL（base64），供 JSON 接口上传图片用。 */
    fun readBase64(path: String): String

    /** 把 data URL / 裸 base64 解码写入 outputPath，返回文件路径。 */
    fun saveBase64(data: String, outputPath: String): String
    fun compressImage(path: String, quality: Int): String
    fun mergePdf(paths: List<String>, output: String): String
    fun openDir(path: String)
    fun listFiles(path: String): String
    fun copyPath(src: String, dst: String)
    fun deletePath(path: String, recursive: Boolean): Boolean
    fun mkdir(path: String)
    fun renamePath(src: String, dst: String)
    fun zip(paths: List<String>, output: String): String
    fun unzip(zipPath: String, outputDir: String): String
    fun sleep(seconds: Int)

    /** 写入系统剪贴板（应用在前台时可用）。 */
    fun setClipboard(text: String)

    /** 读取系统剪贴板文本；无内容返回空串。 */
    fun getClipboard(): String

    /** 点亮屏幕。成功返回「屏幕已点亮」，失败返回中文错误说明。 */
    fun wakeScreen(): String

    /** 本地图片 OCR（中文 + 拉丁字母）。成功返回识别文本，失败返回 {"error":"..."}。 */
    fun ocrText(path: String): String

    /**
     * 本地图片 OCR（带坐标）。成功返回 JSON 数组：
     * `[{"text":"..","x":..,"y":..,"w":..,"h":..}]`，坐标为图片像素（已按 EXIF 方向校正），
     * 失败返回 {"error":"..."}。
     */
    fun ocrBoxes(path: String): String
}

/**
 * L0 —— 本地图像工具箱（纯 Android SDK，不联网、不上传）。
 *
 * 每个操作读取源图片/PDF，写出新文件到源目录（或显式输出路径），返回输出路径；
 * 失败抛中文错误，引擎会转成 `{"__error":"中文说明"}` 返回脚本。
 * `compress` 已在 [ToolBridge.compressImage]，这里不重复暴露。
 */
interface ImageToolboxBridge {
    /** 缩放：keepAspect=true 时按比例适配 width×height（只给一边则另一边自动算）。 */
    fun resize(path: String, width: Int = 0, height: Int = 0, keepAspect: Boolean = true): String

    /** 裁剪：x/y 为左上角，width/height 为裁剪尺寸（须在图片范围内）。 */
    fun crop(path: String, x: Int = 0, y: Int = 0, width: Int = 0, height: Int = 0): String

    /** 任意角度旋转，自动扩画布。 */
    fun rotate(path: String, degrees: Float = 90f): String

    /** 翻转：horizontal=true 左右翻，false 上下翻。 */
    fun flip(path: String, horizontal: Boolean = true): String

    fun grayscale(path: String): String

    /** 方框模糊（三次分离卷积近似高斯），radius 单位像素。 */
    fun blur(path: String, radius: Int = 8): String

    /** 3×3 锐化卷积，amount 推荐 0.1–5。 */
    fun sharpen(path: String, amount: Float = 1f): String

    /** 像素化：blockSize 为方块边长（>=2）。 */
    fun pixelate(path: String, blockSize: Int = 12): String

    /** 亮度/对比度：brightness、contrast 取 -100..100。 */
    fun brightnessContrast(path: String, brightness: Int = 0, contrast: Int = 0): String

    /** 饱和度：0=灰度，1=原图，>1 更艳（封顶 10）。 */
    fun saturation(path: String, factor: Float = 1f): String

    /** 色相旋转（-360..360 度）。 */
    fun hue(path: String, degrees: Float = 0f): String

    /** 自动对比度：按每通道 0.5% 截断拉伸。 */
    fun autoContrast(path: String): String

    fun invert(path: String): String
    fun sepia(path: String): String

    /**
     * 文字水印。position：top-left / top-right / bottom-left / bottom-right / center；
     * alpha 0..255；textSize<=0 时按图片宽度自适应；colorHex 如 #FFFFFF。
     */
    fun watermark(
        path: String,
        text: String,
        position: String = "bottom-right",
        alpha: Int = 160,
        textSize: Int = 0,
        colorHex: String = "#FFFFFF",
    ): String

    /** 纯色边框：width 为单边像素宽，colorHex 如 #FFFFFF。 */
    fun border(path: String, width: Int = 16, colorHex: String = "#FFFFFF"): String

    /** 圆角：radius 像素，超出短边一半时取一半；输出默认 png 以保留透明角。 */
    fun roundCorners(path: String, radius: Int = 48): String

    /** 格式转换：format 只支持 png / jpg / webp，quality 1..100（仅 jpg/webp 生效）。 */
    fun convert(path: String, format: String, quality: Int = 90): String

    /** 重编码以丢弃 EXIF 等元数据（解码+再编码，输出保持源格式）。 */
    fun stripMetadata(path: String): String

    /** 多张图片合成一个 PDF（A4 竖版居中，页序即数组顺序）。 */
    fun imagesToPdf(paths: List<String>, output: String = ""): String

    /** PDF 逐页渲染为 PNG，返回页图路径列表（输出目录缺省为 PDF 所在目录）。 */
    fun pdfToImages(path: String, outputDir: String = ""): List<String>

    /** PDF 页数。 */
    fun pdfPageCount(path: String): Int

    /**
     * 单图处理（依赖包扩展）：[op] 为操作名，[paramsJson] 为参数对象 JSON。
     * 覆盖滤镜/预设/效果/几何等纯 Android SDK 可实现的 ImageToolbox 能力，
     * 图像类 op 返回输出路径，分析类 op 返回 JSON；见 docs/image-toolbox.md。
     */
    fun process(path: String, op: String, paramsJson: String = "{}"): String

    /** 多图合成（依赖包扩展）：[inputsJson] 为输入路径 JSON 数组，返回输出路径。 */
    fun compose(op: String, inputsJson: String, paramsJson: String = "{}"): String

    /** 只读分析（依赖包扩展）：[query] 见 docs/image-toolbox.md，返回 JSON 字符串，不写文件。 */
    fun analyze(path: String, query: String, paramsJson: String = "{}"): String

    /** PDF 编辑（依赖包扩展）：[source] 为 PDF 路径，重新渲染后写出新 PDF，返回输出路径。 */
    fun pdfEdit(op: String, source: String, paramsJson: String = "{}"): String
}

interface UiBridge {
    /** items 为组件列表，返回用户填好的值；用户取消返回 null */
    fun form(title: String, items: List<Map<String, Any?>>): Map<String, Any?>?
    /** 危险操作二次确认 */
    fun confirm(title: String, message: String, danger: Boolean = false): Boolean
    fun progress(ratio: Float, label: String)
    fun show(card: Map<String, Any?>)

    /**
     * 发布当前自动化步骤到宿主悬浮看板（桌面等无宿主 UI 时可保持默认 no-op）。
     * 连续重复的 label 由宿主侧去重，`detail` 可选补充说明。
     */
    fun automationStatus(label: String, detail: String = "") {}

    /** 用户是否在浮窗看板点了「停止」；长脚本可在每步之间轮询以便尽早退出。 */
    fun isCancelled(): Boolean = false
}

/** 白名单组件，CI 扫描 card 时校验 type 必须在此集合内 */
object UiWidgets {
    val ALLOWED: Set<String> = heizige.kk.khatkit.card.UiWidgetVocabulary.ALLOWED
}

interface DownloadBridge {
    /** 立即返回 handle，不阻塞脚本（宿主 Kotlin 侧用） */
    fun enqueue(task: Map<String, Any?>): DownloadHandle

    /** 下载中心观察所有任务（含脚本创建的下载）。 */
    fun observeTasks(): kotlinx.coroutines.flow.StateFlow<List<DownloadTaskInfo>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    /** 从下载中心删除记录（活动任务先取消）。 */
    fun remove(id: String): Boolean = false
    fun list(state: String? = null): List<DownloadHandle>
    fun query(id: String): DownloadHandle?

    // ---- 脚本友好 API：handle 对象跨引擎会被 JSON 化，脚本只能用 id 型接口 ----

    /** 入队并立即返回 id */
    fun start(task: Map<String, Any?>): String

    /**
     * 查询状态；waitSeconds > 0 时最多阻塞等待这么久。
     * 返回 { id, name, state, progress, speed, file, error }
     */
    fun status(id: String, waitSeconds: Int): Map<String, Any?>

    fun pause(id: String): Boolean
    fun resume(id: String): Boolean
    fun cancel(id: String): Boolean
}

interface DownloadHandle {
    val id: String
    /** "done" / "running" / "failed" */
    fun await(seconds: Int): String
    fun progress(): Float
    fun speed(): Long
    fun pause()
    fun resume()
    fun cancel()
    fun file(): String?
}

interface StoreBridge {
    /** 底层 key 自动加 card_<name>_ 前缀，脚本改不了别人的数据 */
    fun kvGet(key: String, default: String? = null): String?
    fun kvSet(key: String, value: String)

    fun fileRead(name: String): String?
    fun fileWrite(name: String, content: String)

    fun dbQuery(table: String, where: String, args: List<Any?>): List<Map<String, Any?>>
    fun dbInsert(table: String, row: Map<String, Any?>)

    /** 敏感数据走 Keystore / 系统钥匙串，不进 kv */
    fun secretGet(key: String): String?
    fun secretSet(key: String, value: String)

    // 跨卡片共享区：写进自己命名空间，读可跨卡片按名取（工作流协作）
    fun sharedWrite(name: String, content: String)
    fun sharedRead(name: String): String?
    fun sharedList(): List<String>
    fun sharedDelete(name: String): Boolean
}

interface ShizukuBridge {
    /** 动作级 API */
    fun setAppEnabled(pkg: String, enabled: Boolean): String
    fun settingsPut(namespace: String, key: String, value: String): String
    fun pm(action: String, pkg: String): String

    /**
     * 以 shell 身份执行命令（`/system/bin/sh -c`），返回合并后的 stdout/stderr
     * 与退出码（`[exit N]` 前缀）。高风险通用能力，仅 elevated 卡片可声明使用；
     * Shizuku 不可用或执行失败返回 `[error] 中文说明`。
     */
    fun shell(cmd: String): String
}

/**
 * L1 —— 无障碍自动化。需要用户在系统设置里显式开启本应用的无障碍服务。
 *
 * 只暴露动作级 API（找节点/点击/输入/手势），不暴露通用注入，脚本无法越过
 * 无障碍框架执行任意操作。节点查询统一用 `Map`（text / desc / viewId /
 * className / clickable / exact），返回的节点字段含 depth/bounds/text/desc 等。
 */
interface AccessibilityBridge {
    /** 服务在线（true 时才应该挂进 BridgeRegistry）。 */
    fun isAvailable(): Boolean

    /** 当前前台包名；拿不到返回 null。 */
    fun currentPackage(): String?

    /** 当前活动窗口的全部节点（扁平化，含 depth/bounds）。 */
    fun dumpWindow(): List<Map<String, Any?>>

    /** 按条件查找节点。 */
    fun findNodes(query: Map<String, Any?>): List<Map<String, Any?>>

    /** 点击第一个匹配节点（不可点击时向上找可点击祖先）。 */
    fun click(query: Map<String, Any?>): Boolean

    /** 长按第一个匹配节点。 */
    fun longClick(query: Map<String, Any?>): Boolean

    /** 给第一个可编辑的匹配节点设置文本。 */
    fun setText(query: Map<String, Any?>, text: String): Boolean

    /** 坐标点击。 */
    fun tap(x: Float, y: Float): Boolean

    /** 坐标滑动，durationMs 为手势时长。 */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean

    /** 滚动当前窗口：forward / backward / up / down / left / right。 */
    fun scroll(direction: String): Boolean

    /** 全局动作：back / home / recents / notifications。 */
    fun globalAction(action: String): Boolean

    /** 启动应用主界面。 */
    fun openApp(packageName: String): Boolean

    /** 轮询等待首个匹配节点出现，超时返回 null。 */
    fun waitForNode(query: Map<String, Any?>, timeoutMs: Long): Map<String, Any?>?

    /** 坐标长按 durationMs 毫秒（100~10000）。 */
    fun press(x: Float, y: Float, durationMs: Long): Boolean

    /**
     * 执行多段手势。strokesJson 为 JSON 数组：
     * `[ [ {"x":1,"y":2,"t":0}, {"x":3,"y":4,"t":500} ], ... ]`
     * t 为段内毫秒偏移；超过系统段数上限的轨迹会被忽略。
     */
    fun gesture(strokesJson: String): Boolean

    /** 等待窗口内容稳定（连续两次节点摘要一致）或超时。 */
    fun waitForIdle(timeoutMs: Long): Boolean

    /** 等待指定包名成为前台应用。 */
    fun waitForPackage(packageName: String, timeoutMs: Long): Boolean

    /**
     * 截屏并保存到共享存储，返回 PNG 路径。
     * API 30 以下或失败时返回中文错误文本，不抛异常。
     */
    fun captureScreen(outputPath: String? = null): String

    /**
     * 在当前屏幕上做模板匹配（多尺度灰度归一化互相关，纯 Kotlin 无 OpenCV）。
     * 命中返回 `{"found":true,"x":..,"y":..,"score":..}`（x/y 为屏幕像素中心坐标），
     * 未命中返回 `{"found":false}`，参数或截图失败返回 `{"error":"..."}`。
     */
    fun findImage(templatePath: String, threshold: Double = 0.9): String

    /**
     * 查找模板并点击其中心；timeoutMs > 0 时每 300ms 轮询一次直到超时。
     * 返回是否点击成功。
     */
    fun tapImage(templatePath: String, threshold: Double = 0.9, timeoutMs: Long = 0): Boolean

    /**
     * 查找指定颜色的第一个像素（每通道容差 tolerance）。
     * region 为空表示全屏，或传 "x,y,w,h" 限定区域。
     * 命中返回 `{"found":true,"x":..,"y":..,"color":"#RRGGBB"}`，未命中返回 `{"found":false}`。
     */
    fun findColor(colorHex: String, tolerance: Int = 16, region: String = ""): String

    /** 对当前聚焦的输入框执行粘贴。 */
    fun paste(): Boolean

    /**
     * 用无障碍服务的 WindowManager 添加悬浮窗（`TYPE_ACCESSIBILITY_OVERLAY`，
     * 层级在状态栏/通知栏之上）。添加失败返回 false，不抛异常。
     */
    fun addOverlay(view: View, params: WindowManager.LayoutParams): Boolean

    /** 移除由 [addOverlay] 添加的悬浮窗；失败静默。 */
    fun removeOverlay(view: View)
}

interface RootBridge {
    fun shell(cmd: String): String
}
