package heizige.kk.khatkit.app.feature.yunx

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.app.core.ui.components.khatkit.KhatKitUiHost
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.theme.ProvideMaterial3Theme
import heizige.kk.khatkit.app.feature.tools.data.ToolCatalogueRepository
import heizige.kk.khatkit.app.feature.tools.helper.isUserTool
import heizige.kk.khatkit.bridge.impl.BridgeFactory
import heizige.kk.khatkit.exec.CardRunManager
import heizige.kk.khatkit.hub.CardCache
import heizige.kk.khatkit.ui.UiBridgeHost
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The installed WASM, real JNI interpreter and shipping Mochi renderer on the phone. */
class YunxUiDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun installedCardRendersThreePages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val provider = appEntryPoint(context).khatKitToolProvider()
        runBlocking {
            val repository = ToolCatalogueRepository(context, provider)
            val entry = repository.load().cards.first { it.name == "yunx_parallel_download" }
            assertEquals("3.0.1", entry.version)
            assertTrue(entry.isUserTool())
            assertTrue(provider.installCard(entry, force = true))
            val installed = repository.installed().first { it.name == entry.name }
            assertTrue(installed.isUserTool())
        }
        val host = provider.javaClass.getDeclaredField("uiHost").apply { isAccessible = true }.get(provider) as UiBridgeHost
        assertNull(host.request.value)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        compose.setContent {
            ui.feice.mochi.theme.MochiTheme {
                ProvideMaterial3Theme(amoled = false) {
                    CompositionLocalProvider(LocalSettings provides Settings.dummy()) { KhatKitUiHost() }
                }
            }
        }
        compose.waitUntil(10_000) { host.hostAttached.value }
        try {
            val executor = runBlocking { BridgeFactory.create(context, scope, host) }
            executor.bridges.registerDynamic("yunx", YunxCardBridge(context))
            val manager = CardRunManager(scope, maxConcurrent = 1) { card, args -> executor.execute(card, args) }
            val card = CardCache(File(context.filesDir, "khatkit")).installedCards()
                .first { it.manifest.name == "yunx_parallel_download" }
            assertEquals("3.0.1", card.manifest.version)
            val job = scope.async { manager.run(card, emptyMap()) }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("解析分享").fetchSemanticsNodes().isNotEmpty() || job.isCompleted
            }
            if (job.isCompleted) fail(runBlocking { job.await().toString() })
            compose.waitForIdle()
            fun screenshot(name: String) {
                val dir = File(context.getExternalFilesDir(null), "yunx-evidence").apply { mkdirs() }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            screenshot("home")
            compose.onAllNodes(hasSetTextAction())[0].performTextInput("https://example.org/not-a-share")
            compose.onNode(hasText("解析分享") and hasClickAction()).performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("未识别到 YunX 支持的分享链接").fetchSemanticsNodes().isNotEmpty() || job.isCompleted
            }
            assertFalse("Native bridge errors should remain on the input page", job.isCompleted)
            compose.onNodeWithText("账号", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("网盘账号").fetchSemanticsNodes().isNotEmpty() }
            screenshot("accounts")
            compose.onNodeWithText("记录", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("还没有解析记录").fetchSemanticsNodes().isNotEmpty() }
            screenshot("history")
            compose.onNodeWithText("解析", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("解析分享").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(job.isCompleted)
        } finally {
            scope.cancel()
            runBlocking { scope.coroutineContext[Job]!!.join() }
            while (host.request.value != null) host.dismiss()
        }
    }
}
