package heizige.kk.khatkit.dependencies.imageToolbox

import org.junit.Assert.*
import org.junit.Test

class RasterToolsTest {
    @Test fun tilesCoverEveryPixelExactlyOnce() {
        val covered = IntArray(7 * 5)
        val tiles = RasterTools.tiles(7, 5, 3, 2)
        assertEquals(6, tiles.size)
        tiles.forEach { tile ->
            for (y in tile.y until tile.y + tile.height) {
                for (x in tile.x until tile.x + tile.width) covered[y * 7 + x]++
            }
        }
        assertTrue(covered.all { it == 1 })
    }

    @Test fun invalidAndExcessiveGridsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { RasterTools.tiles(2, 2, 3, 1) }
        assertThrows(IllegalArgumentException::class.java) { RasterTools.tiles(100, 100, 17, 17) }
        assertThrows(IllegalArgumentException::class.java) { RasterTools.tiles(2, 2, 0, 1) }
    }

    @Test fun identicalImagesHaveZeroErrorAndInfinitePsnr() {
        val pixels = intArrayOf(0xff112233.toInt(), 0x80224466.toInt())
        val stats = RasterTools.compare(pixels, pixels, 0)
        assertEquals(0, stats.changedPixels)
        assertEquals(0.0, stats.mae, 0.0)
        assertEquals(0.0, stats.mse, 0.0)
        assertNull(stats.psnr)
    }

    @Test fun comparisonTracksRgbAndAlphaDifferences() {
        val result = RasterTools.compare(intArrayOf(0xff000000.toInt()), intArrayOf(0xffffffff.toInt()), 0)
        assertEquals(1, result.changedPixels)
        assertEquals(255.0, result.mae, 0.0)
        assertEquals(65025.0, result.mse, 0.0)
        assertEquals(0.0, result.psnr!!, 0.0)
        assertEquals(1, RasterTools.compare(intArrayOf(0), intArrayOf(0xff000000.toInt()), 0).changedPixels)
        assertEquals(0, RasterTools.compare(intArrayOf(0xff000000.toInt()), intArrayOf(0xff010101.toInt()), 1).changedPixels)
    }

    @Test fun invalidComparisonsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { RasterTools.compare(intArrayOf(), intArrayOf(), 0) }
        assertThrows(IllegalArgumentException::class.java) { RasterTools.compare(intArrayOf(0), intArrayOf(0, 1), 0) }
        assertThrows(IllegalArgumentException::class.java) { RasterTools.compare(intArrayOf(0), intArrayOf(0), -1) }
    }

    @Test fun identityLutPreservesInterpolatedColorsAndAlpha() {
        val lut = CubeLut.parse(cube())
        listOf(0, -1, 0x80123456.toInt(), 0xffabcde0.toInt()).forEach {
            assertEquals(it, lut.apply(it, 1.0))
        }
    }

    @Test fun inversionLutUsesRedFastestOrderingAndStrength() {
        val lut = CubeLut.parse(cube(invert = true))
        assertEquals(0x80edcba9.toInt(), lut.apply(0x80123456.toInt(), 1.0))
        assertEquals(0x80123456.toInt(), lut.apply(0x80123456.toInt(), 0.0))
        assertEquals(0x80808080.toInt(), lut.apply(0x80123456.toInt(), 0.5))
    }

    @Test fun cubeDomainIsRespected() {
        val lut = CubeLut.parse(cube().replace("DOMAIN_MAX 1 1 1", "DOMAIN_MAX 0.5 0.5 0.5"))
        assertEquals(0xff808080.toInt(), lut.apply(0xff404040.toInt(), 1.0))
    }

    @Test fun malformedCubeFilesAreRejected() {
        listOf("LUT_3D_SIZE 100", "LUT_1D_SIZE 2", "LUT_3D_SIZE 2\n0 0 0",
            cube() + "\n0 0 0", cube().replace("DOMAIN_MAX 1 1 1", "DOMAIN_MAX 0 1 1"),
            cube().replace("0 0 0 # sample", "NaN 0 0 # sample")).forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { CubeLut.parse(text) }
        }
        assertThrows(IllegalArgumentException::class.java) { CubeLut.parse(cube()).apply(0, Double.NaN) }
    }

    private fun cube(invert: Boolean = false): String = buildString {
        appendLine("TITLE \"test LUT\"")
        appendLine("LUT_3D_SIZE 2")
        appendLine("DOMAIN_MIN 0 0 0")
        appendLine("DOMAIN_MAX 1 1 1")
        for (blue in 0..1) for (green in 0..1) for (red in 0..1) {
            if (invert) appendLine("${1 - red} ${1 - green} ${1 - blue}")
            else appendLine("$red $green $blue # sample")
        }
    }
}
