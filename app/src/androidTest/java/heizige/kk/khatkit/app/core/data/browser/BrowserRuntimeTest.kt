package heizige.kk.khatkit.app.core.data.browser

import android.webkit.WebView
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import heizige.kk.khatkit.app.RouteActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.io.File
import java.util.UUID

/** Real device WebViews with local HTML; no credentials or remote requests. */
class BrowserRuntimeTest {
    @get:Rule val activity = ActivityScenarioRule(RouteActivity::class.java)

    @Test
    fun threeTabsCanTypeClickAndReadIndependentLiveSnapshots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val historyFile = File(context.cacheDir, "browser-test-${UUID.randomUUID()}.json")
        val database = Room.inMemoryDatabaseBuilder(context, BrowserArchiveDatabase::class.java).build()
        val archive = RoomBrowserArchive(database, historyFile)
        val runtime = BrowserRuntime(
            context,
            archive = archive,
            webViewFactory = { ctx ->
                object : WebView(ctx) {
                    override fun loadUrl(url: String) {
                        loadDataWithBaseURL(url, """
                            <html><head><meta name="viewport" content="width=device-width"><title>Fixture</title></head>
                            <body><input id="account"><input id="password" type="password">
                            <button id="login" onclick="setTimeout(()=>document.getElementById('result').textContent='Logged in as '+document.getElementById('account').value,200)">Login</button>
                            <p id="result">Not logged in</p>
                            ${List(150) { "<p>商品😀 product $it</p>" }.joinToString("")}
                            </body></html>
                        """.trimIndent(), "text/html", "UTF-8", url)
                    }
                }
            },
        )
        val ids = mutableListOf<String>()
        try {
            ids += coroutineScope {
                (1..3).map { index -> async {
                    Json.parseToJsonElement(runtime.open("https://browser-fixture.test/tab-$index"))
                        .jsonObject.getValue("id").jsonPrimitive.content
                } }.awaitAll()
            }
            assertEquals(3, ids.toSet().size)
            assertEquals(3, runtime.uiState.value.tabs.size)
            runtime.select(ids[0])
            assertEquals(ids[0], runtime.uiState.value.activeId)
            withContext(Dispatchers.Main.immediate) {
                val firstHost = FrameLayout(context)
                val secondHost = FrameLayout(context)
                runtime.attach(ids[0], firstHost)
                val originalView = firstHost.getChildAt(0)
                runtime.attach(ids[0], secondHost)
                assertEquals(0, firstHost.childCount)
                assertSame(originalView, secondHost.getChildAt(0))
                secondHost.removeAllViews()
            }
            coroutineScope {
                ids.mapIndexed { index, id -> async {
                    val tab = runtime.forSession(id)
                    assertFalse(tab.type("#account", "user-$index").contains("error"))
                    assertFalse(tab.type("#password", "test-only-secret-$index").contains("error"))
                    assertFalse(tab.click("#login").contains("error"))
                    tab.waitFor("#result", "Logged in as user-$index", 5000)
                    val text = tab.extractText()
                    assertTrue(text, text.contains("Logged in as user-$index"))
                    assertFalse(text, text.contains("test-only-secret"))
                    val snapshot = tab.snapshot()
                    assertTrue(snapshot.toByteArray().size < 20_000)
                    assertTrue(snapshot, snapshot.contains("Logged in as user-$index"))
                    val semantic = Json.parseToJsonElement(snapshot).jsonObject.getValue("snapshot").jsonObject
                    val login = semantic.getValue("elements").jsonArray.first {
                        it.jsonObject["id"] == JsonPrimitive("login")
                    }.jsonObject.getValue("ref").jsonPrimitive.content
                    assertFalse(tab.click(login).contains("error"))
                    val screenshot = tab.screenshot()
                    val image = File(requireNotNull(Uri.parse(screenshot.imageUrl).path))
                    try {
                        val bitmap = BitmapFactory.decodeFile(image.absolutePath)
                        assertNotNull(bitmap)
                        try {
                            assertEquals(1080, bitmap.width)
                            assertEquals(1920, bitmap.height)
                            // Blank or fully transparent capture must not pass as a rendered page.
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            assertTrue("Screenshot is blank", pixels.toSet().size > 1)
                        } finally {
                            bitmap.recycle()
                        }
                        assertEquals(id, Json.parseToJsonElement(screenshot.metadata).jsonObject["id"]?.jsonPrimitive?.content)
                    } finally {
                        image.delete()
                    }
                } }.awaitAll()
            }
            runtime.close(ids[1])
            assertEquals(2, Json.parseToJsonElement(runtime.listSessions()).jsonArray.size)
            assertEquals(2, runtime.uiState.value.tabs.size)
            assertTrue(runtime.forSession(ids[0]).extractText().contains("Logged in as user-0"))
            assertEquals(3, archive.history().size)
            val firstTab = runtime.forSession(ids[0])
            val old = Json.parseToJsonElement(firstTab.snapshot()).jsonObject.getValue("snapshot").jsonObject
            val oldRef = old.getValue("elements").jsonArray.first().jsonObject.getValue("ref").jsonPrimitive.content
            firstTab.navigate("https://browser-fixture.test/reloaded")
            assertTrue(firstTab.click(oldRef).contains("Stale element reference"))
            val reset = Json.parseToJsonElement(firstTab.snapshot(null, 0,
                old.getValue("snapshot_id").jsonPrimitive.content)).jsonObject.getValue("snapshot").jsonObject
            assertEquals("unknown_baseline", reset["reset_reason"]?.jsonPrimitive?.content)
        } finally {
            ids.forEach { runtime.close(it) }
            historyFile.delete()
            database.close()
        }
    }
}
