package heizige.kk.khatkit.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every Activity must disable Android's system-bar contrast scrim for Mochi edge-to-edge effects. */
class EdgeToEdgeSourceGuardTest {
    @Test
    fun appActivitiesUseMochiEdgeToEdgeBeforeSuperOnCreate() {
        val activitySources = listOf(
            "src/main/java/heizige/kk/khatkit/app/RouteActivity.kt",
            "src/main/java/heizige/kk/khatkit/app/core/ui/activity/SafeModeActivity.kt",
        )

        activitySources.forEach { relativePath ->
            val source = File(relativePath).readText()
            val setup = source.indexOf("mochiEnableEdgeToEdge()")
            val superOnCreate = source.indexOf("super.onCreate(savedInstanceState)")

            assertTrue("$relativePath must call mochiEnableEdgeToEdge()", setup >= 0)
            assertTrue("$relativePath must configure edge-to-edge before super.onCreate", setup < superOnCreate)
            assertFalse(
                "$relativePath must not use Android's contrast-scrim default",
                source.contains("import androidx.activity.enableEdgeToEdge"),
            )
        }
    }
}

/**
 * ARCHITECTURE.md §8 在 Mochi 落地后的源码守门：
 * route 返回/转场只由唯一的 MochiNavHost 持有；页面级返回由 PageBackInterceptor/PageBackGuard
 * 先消费；不能用 NavigationEvent.progress 单独驱动 route 过渡。
 */
class NavigationSourceGuardTest {
    @Test
    fun routePopIsOwnedByASingleMochiNavHost() {
        val source = File("src/main/java/heizige/kk/khatkit/app/RouteActivity.kt").readText()

        assertTrue("RouteActivity must render exactly one MochiNavHost", source.split("MochiNavHost(").size - 1 == 1)
        assertFalse("Nav3 NavDisplay must not come back", source.contains("NavDisplay("))
        assertFalse(
            "route transition must not be driven by NavigationEvent.progress",
            source.contains("NavigationEvent.progress"),
        )
        assertTrue(
            "page back guard must gate the route predictive handler on top-page overlays",
            source.contains("PageBackInterceptor.hasHandlersFor(topRoute)"),
        )
        assertTrue(
            "Mochi route predictive handling must be disabled while a page overlay owns back",
            source.contains("predictiveBack = settings.displaySetting.mochiPredictiveBack && !hasOverlay"),
        )
    }

    @Test
    fun predictiveBackCallbackIsEnabledForSystemGesture() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "manifest must opt into the platform predictive back callback",
            manifest.contains("android:enableOnBackInvokedCallback=\"true\""),
        )
    }

    @Test
    fun mediaPickerKeepsAlbumBeforeSelectionInBackOrder() {
        val picker = File("../mediapicker/src/main/java/heizige/kk/khatkit/mediapicker/ui/KhatKitMediaPicker.kt")
        if (picker.exists()) {
            val pickerSource = picker.readText()
            assertTrue(pickerSource.contains("PredictiveBackHandler(enabled = isSearching)"))
            assertTrue(pickerSource.contains("PredictiveBackHandler(enabled = !isSearching && selectedAlbumId != ALL_ALBUM_ID)"))
        }

        val gridPath = "../mediapicker/src/main/java/heizige/kk/khatkit/mediapicker/ui/components/MediaPickerGridWithOverlays.kt"
        val grid = File(gridPath)
        if (grid.exists()) {
            val gridSource = grid.readText()
            assertTrue(
                "selection back handler must yield to the album back handler",
                gridSource.contains("selectedAlbumId == ALL_ALBUM_ID"),
            )
        }
    }
}
