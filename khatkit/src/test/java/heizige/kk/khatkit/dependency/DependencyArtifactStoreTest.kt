package heizige.kk.khatkit.dependency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 依赖产物 sha256 校验 + 只读化（Android 14+ W^X 动态代码加载要求代码文件只读）。
 * 纯 JVM：用临时目录模拟 `filesDir/dependencies/<name>/`。
 */
class DependencyArtifactStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sha256MatchesKnownDigestAndDetectsTamper() {
        val file = tempFolder.newFile("1.0.0.jar")
        file.writeBytes("abc".toByteArray(Charsets.UTF_8))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DependencyArtifactStore.sha256(file),
        )
        val original = DependencyArtifactStore.sha256(file)
        file.writeBytes("abx".toByteArray(Charsets.UTF_8))
        assertNotEquals(original, DependencyArtifactStore.sha256(file))
        assertEquals(
            DependencyArtifactStore.sha256(file.readBytes()),
            DependencyArtifactStore.sha256(file),
        )
    }

    @Test
    fun writeAtomicallyFlushesAndReplacesTarget() {
        val target = tempFolder.newFile("2.0.0.jar")
        target.writeText("old-content")
        val bytes = "new-content".toByteArray(Charsets.UTF_8)
        DependencyArtifactStore.writeAtomically(target, bytes)
        assertTrue(target.readBytes().contentEquals(bytes))
        assertFalse(File(target.parentFile, "2.0.0.jar.tmp").exists())
    }

    @Test
    fun markReadOnlyClearsWriteBitAndKeepsContentReadable() {
        // root 会绕过权限位，无法在 CI 以 root 运行时做该断言
        assumeFalse("以 root 运行时无法校验只读位", System.getProperty("user.name") == "root")
        val file = tempFolder.newFile("3.0.0.jar")
        file.writeText("dex")
        assertTrue(file.canWrite())

        assertTrue(DependencyArtifactStore.markReadOnly(file))
        assertFalse(file.canWrite())
        // 复用只读产物：不重写也能读取，sha256 保持一致
        assertEquals("dex", file.readText())
        assertTrue(DependencyArtifactStore.markReadOnly(file))
    }

    @Test
    fun containsClassesDexRequiresRootEntry() {
        val valid = tempFolder.newFile("valid.jar")
        ZipOutputStream(valid.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(byteArrayOf(0x64, 0x65, 0x78))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/khatkit-dependency.properties"))
            zip.closeEntry()
        }
        assertTrue(DependencyArtifactStore.containsClassesDex(valid))

        val nested = tempFolder.newFile("nested.jar")
        ZipOutputStream(nested.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("nested/classes.dex"))
            zip.closeEntry()
        }
        assertFalse(DependencyArtifactStore.containsClassesDex(nested))
        assertFalse(DependencyArtifactStore.containsClassesDex(tempFolder.newFile("not-zip.jar")))
    }
}
