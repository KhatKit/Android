package heizige.kk.khatkit.app.feature.chat

import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.RouteActivity
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.ai.ui.UIMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * C1-09 收尾：**真实调用生产 [shareFile] 后，系统分享 chooser 到底有没有真的弹出来**。
 *
 * ## 为什么需要这一条
 *
 * 它填的正是 C1-09 迄今唯一没被自动化覆盖的一格：系统 `ACTION_SEND` 选择面板
 * （`Intent.createChooser`）的真实弹出。既有两类证据都不够：
 * - `GroupExportShareIntentSourceGuardTest`（JVM）：只对源码文本做断言，不执行；
 * - `C1GroupQrBitmapRoundTripDeviceTest#exportTempFileUriIsReadable...`（真机）：自述
 *   「不 startActivity，避免弹系统分享面板」，只对**同一形状**的 Intent 断言。
 *
 * 本类**真的**用 `ActivityScenario` 拉起 `RouteActivity`，在它的 Activity context 上
 * 调用**生产** [shareFile]（`ConversationExport.kt`，`GroupExportCard.kt:218` 同一份），
 * 再用 framework 级 `android.app.UiAutomation`（**不引入任何新依赖**）跑
 * `dumpsys activity activities`，断言系统 resolver 已出现，且它的
 * `Intent { act=android.intent.action.CHOOSER ... }` 的 `launchedFromPackage`
 * **就是被测包**——这正是「我们的分享真的被系统接住并弹了面板」的硬证据。
 *
 * ## 为什么从 Activity context 调，而不是 targetContext
 *
 * 生产 `shareFile` 直接 `context.startActivity(...)`，**没有**加 `FLAG_ACTIVITY_NEW_TASK`。
 * 从非 Activity 的 `targetContext` 调会抛
 * `AndroidRuntimeException: Calling startActivity() from outside of an Activity context
 * requires the FLAG_ACTIVITY_NEW_TASK flag`。所以本类必须拿到真实 Activity 的 context——
 * 这也正是生产调用点（`GroupExportCard` 在 Composable 里用 `LocalContext.current`）的情形。
 *
 * ## 不证明什么
 *
 * - 不点选采具体分享目标（那是“档 2”）。
 * - 不改 `app/src/main`；只为让面板可被观察，先把屏幕唤醒并解除锁屏（系统面板本身原样保留）。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupShareChooserDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val prettyJson = Json { prettyPrint = true }

    @Test
    fun realProductionShareFileOpensSystemChooser() {
        val ui: UiAutomation = instrumentation.uiAutomation

        // ---------- ⓪ 让面板可被观察：唤醒 + 解除锁屏（不改任何系统设置） ----------
        shellUi(ui, "input keyevent KEYCODE_WAKEUP")
        shellUi(ui, "wm dismiss-keyguard")

        // ---------- ① 生产 IO 助手写临时文件 + FileProvider URI ----------
        val fileName = "c1-chooser-share.jsonl"
        val nodes = listOf(
            MessageNode.of(UIMessage.user("@阿尔法 先给结论")),
            MessageNode.of(UIMessage.assistant("阿尔法：结论在附注").copy(roleId = "a")),
        )
        val config = GroupConfig(
            roles = listOf(GroupRole(id = "a", name = "阿尔法", assistantId = "asst-a")),
            mode = GroupChat.MODE_PIPELINE,
            chairRoleId = "a",
            tokenBudgetPerRound = 4096,
        )
        val exported = TavernChatCodec.exportGroupJsonl(
            nodes = nodes,
            config = config,
            cards = emptyList(),
            userName = "阿达",
            groupName = "C1 chooser 群",
            createDate = null,
        )
        val bytes = exported.toByteArray(Charsets.UTF_8)
        assertTrue("导出夹具不应为空", bytes.isNotEmpty())
        val uri = writeExportTempFile(context, fileName) { it.write(bytes) }
        assertTrue("必须是 FileProvider content:// URI，实际=$uri", uri.scheme == "content")

        // ---------- ② 拉起 RouteActivity，取真实 Activity context ----------
        val scenario = ActivityScenario.launch<RouteActivity>(
            Intent(context, RouteActivity::class.java),
        )
        try {
            // ---------- ③ 真实调用生产 shareFile（Activity context，不需要 NEW_TASK） ----------
            scenario.onActivity { activity ->
                shareFile(activity, uri, GROUP_EXPORT_MIME_TYPE)
            }

            // ---------- ④ 轮询系统 chooser 的硬签名 ----------
            val obs = waitForChooser(ui, timeoutMs = 20_000)

            val evidence = buildJsonObject {
                put("case", "C1-09 real ACTION_SEND chooser")
                putJsonObject("device") {
                    put("model", Build.MODEL)
                    put("sdk", Build.VERSION.SDK_INT)
                    put("release", Build.VERSION.RELEASE)
                    put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                    put("fingerprint", Build.FINGERPRINT)
                }
                put("package_under_test", context.packageName)
                putJsonObject("file") {
                    put("uri", uri.toString())
                    put("fileName", fileName)
                    put("bytes", bytes.size)
                    put("sha256", sha256Hex(bytes))
                }
                put("mime", GROUP_EXPORT_MIME_TYPE)
                put("chooser_detected", obs.detected)
                put("top_resumed_activity", obs.topResumed)
                put("current_focus", obs.currentFocus)
                put("chooser_markers", obs.markerDump)
                put("attempts", obs.attempts)
            }
            val dir = File(
                requireNotNull(context.getExternalFilesDir(null)) { "external files dir 为 null" },
                "c1-chooser-share",
            )
            assertTrue("证据目录建不出来：$dir", dir.mkdirs() || dir.isDirectory)
            val reportFile = File(dir, "c1-chooser-share-report.json")
            reportFile.writeText(prettyJson.encodeToString(JsonElement.serializer(), evidence))

            // ---------- ⑤ 硬断言：真实 chooser 起来了 ----------
            assertTrue(
                "调用生产 shareFile 后 20s 内没等到系统 chooser。\n" +
                    "top_resumed=${obs.topResumed}\nfocus=${obs.currentFocus}\n" +
                    "markers=${obs.markerDump}",
                obs.detected,
            )
            assertTrue(
                "chooser 必须是被测包发起的：launchedFromPackage 应为 ${context.packageName}\n" +
                    "markers=${obs.markerDump}",
                obs.markerDump.contains("launchedFromPackage=${context.packageName}"),
            )
            assertTrue(
                "chooser 的 action 必须是 android.intent.action.CHOOSER\nmarkers=${obs.markerDump}",
                obs.markerDump.contains("act=android.intent.action.CHOOSER"),
            )
            assertTrue(
                "chooser 必须由系统 IntentResolver 组件承载（Android 16 = com.android.intentresolver）\n" +
                    "markers=${obs.markerDump}",
                obs.markerDump.contains("com.android.intentresolver"),
            )
        } finally {
            // 不改系统面板状态：只关掉 ActivityScenario，不主动 dismiss chooser。
            runCatching { scenario.close() }
        }
    }

    private data class ChooserObservation(
        val detected: Boolean,
        val topResumed: String,
        val currentFocus: String,
        val markerDump: String,
        val attempts: Int,
    )

    /**
     * 轮询 `dumpsys activity activities`，直到出现「由本包发起、action=CHOOSER、
     * 宿主为 com.android.intentresolver」的 ActivityRecord。
     */
    private fun waitForChooser(ui: UiAutomation, timeoutMs: Long): ChooserObservation {
        val deadline = System.currentTimeMillis() + timeoutMs
        var attempts = 0
        var topResumed = ""
        var currentFocus = ""
        var markerDump = ""
        while (System.currentTimeMillis() < deadline) {
            attempts++
            val activities = shellUi(ui, "dumpsys activity activities")
            val window = shellUi(ui, "dumpsys window")
            topResumed = activities.lineSequence()
                .firstOrNull { it.contains("topResumedActivity") }?.trim().orEmpty()
            currentFocus = window.lineSequence()
                .firstOrNull { it.contains("mCurrentFocus") }?.trim().orEmpty()

            val hasChooser = activities.contains("act=android.intent.action.CHOOSER") &&
                activities.contains("launchedFromPackage=${context.packageName}") &&
                activities.contains("com.android.intentresolver")
            if (hasChooser) {
                markerDump = activities.lineSequence()
                    .filter {
                        it.contains("ChooserActivity") ||
                            it.contains("act=android.intent.action.CHOOSER") ||
                            it.contains("launchedFromPackage=${context.packageName}") ||
                            it.contains("mActivityComponent=com.android.intentresolver")
                    }
                    .joinToString("\n")
                return ChooserObservation(true, topResumed, currentFocus, markerDump, attempts)
            }
            Thread.sleep(250)
        }
        return ChooserObservation(false, topResumed, currentFocus, markerDump, attempts)
    }

    private fun shellUi(ui: UiAutomation, cmd: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(cmd)).use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
