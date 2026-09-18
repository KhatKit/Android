package heizige.kk.khatkit.bridge

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

interface UiBridge {
    /** items 为组件列表，返回用户填好的值；用户取消返回 null */
    fun form(title: String, items: List<Map<String, Any?>>): Map<String, Any?>?
    /** 危险操作二次确认 */
    fun confirm(title: String, message: String, danger: Boolean = false): Boolean
    fun progress(ratio: Float, label: String)
    fun show(card: Map<String, Any?>)
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
}

interface RootBridge {
    fun shell(cmd: String): String
}
