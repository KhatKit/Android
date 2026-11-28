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
