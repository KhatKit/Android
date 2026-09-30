package heizige.kk.khatkit.dependencies.imageToolbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ToolboxWorkflowDeviceTest {
    private val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "workflow-test-${UUID.randomUUID()}").apply { mkdirs() }

    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun splitRoundTripsOddSizedImageAndRefusesOverwrite() {
        val pixels = IntArray(35) { 0xff000000.toInt() or (it * 123456) }
        val path = input("odd.png", 7, 5, pixels)
        val params = mapOf("columns" to 3, "rows" to 2)
        val result = JSONObject(ToolboxWorkflow.multi("split_grid", listOf(path), params))
        val outputs = result.getJSONArray("outputs")
        assertEquals(6, outputs.length())
        RasterTools.tiles(7, 5, 3, 2).forEachIndexed { index, tile ->
            val image = BitmapFactory.decodeFile(outputs.getString(index))
            try {
                assertEquals(tile.width, image.width)
                assertEquals(tile.height, image.height)
                for (y in 0 until tile.height) for (x in 0 until tile.width) {
                    assertEquals(pixels[(tile.y + y) * 7 + tile.x + x], image.getPixel(x, y))
                }
            } finally { image.recycle() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolboxWorkflow.multi("split_grid", listOf(path), params)
        }
    }

    @Test fun comparisonProducesMetricsAndDifferenceImage() {
        val black = input("black.png", 2, 2, IntArray(4) { 0xff000000.toInt() })
        val white = input("white.png", 2, 2, IntArray(4) { -1 })
        val target = File(directory, "diff.png")
        val result = JSONObject(ToolboxWorkflow.multi("difference", listOf(black, white), mapOf("output" to target.path)))
        assertEquals(4, result.getInt("changed_pixels"))
        assertEquals(255.0, result.getDouble("mae_rgb"), 0.0)
        assertTrue(target.isFile)
        val output = BitmapFactory.decodeFile(target.path)
        assertEquals(-1, output.getPixel(0, 0))
        output.recycle()
        val identical = JSONObject(ToolboxWorkflow.multi("compare", listOf(black, black), emptyMap()))
        assertTrue(identical.getBoolean("identical"))
        assertEquals("infinity", identical.getString("psnr_rgb"))
        assertThrows(IllegalArgumentException::class.java) {
            ToolboxWorkflow.multi("difference", listOf(black, white), mapOf("output" to white))
        }
        val smaller = input("small.png", 1, 1, intArrayOf(-1))
        assertThrows(IllegalArgumentException::class.java) {
            ToolboxWorkflow.multi("compare", listOf(black, smaller), emptyMap())
        }
    }

    @Test fun cubeLutAndChainRunOnAndroidBitmaps() {
        val lut = File(directory, "invert.cube").apply {
            writeText(buildString {
                appendLine("LUT_3D_SIZE 2")
                for (b in 0..1) for (g in 0..1) for (r in 0..1) appendLine("${1-r} ${1-g} ${1-b}")
            })
        }
        val source = Bitmap.createBitmap(intArrayOf(0xff123456.toInt()), 1, 1, Bitmap.Config.ARGB_8888)
        try {
            val params = mapOf("lut" to lut.path)
            val inverted = ToolboxWorkflow.process(source, "lut_cube", params)
            assertEquals(0xffedcba9.toInt(), inverted.getPixel(0, 0))
            inverted.recycle()
            val chain = ToolboxWorkflow.process(source, "filter_chain", mapOf("steps" to listOf(
                mapOf("op" to "lut_cube", "params" to params),
                mapOf("op" to "lut_cube", "params" to params),
            )))
            assertEquals(source.getPixel(0, 0), chain.getPixel(0, 0))
            assertFalse(source.isRecycled)
            chain.recycle()
            assertThrows(IllegalArgumentException::class.java) {
                ToolboxWorkflow.process(source, "filter_chain", mapOf("steps" to listOf(mapOf("op" to "filter_chain"))))
            }
            assertFalse(source.isRecycled)
        } finally { source.recycle() }
    }

    private fun input(name: String, width: Int, height: Int, pixels: IntArray): String =
        ToolboxIO.saveAndRecycle(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888), File(directory, name))
}
