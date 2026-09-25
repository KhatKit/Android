package heizige.kk.khatkit.app.dependency

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexClassLoader
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.dependency.DependencyEnsureResult
import heizige.kk.khatkit.dependency.DependencyManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android 14+ 动态代码加载（W^X）回归测试：targetSdk ≥ 34 时 DexClassLoader 拒绝可写代码文件。
 * 验证 [DependencyManager] 下载落盘后会把产物置为私有只读，并能在设备上成功加载。
 *
 * 依赖产物用 instrumentation 测试 APK 自带的 classes.dex 拼装，入口指向测试内嵌类 [Probe]，
 * 保证类确实从动态 jar 的 dex 中解析。
 */
@RunWith(AndroidJUnit4::class)
class DependencyReadOnlyLoadTest {

    @Suppress("unused")
    class Probe {
        fun ping(): String = "pong"
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext = instrumentation.targetContext
    private val testApk = File(instrumentation.context.applicationInfo.sourceDir)

    private fun dependencyJarBytes(): ByteArray {
        val descriptor = "L${Probe::class.java.name.replace('.', '/')};"
        val dex = ZipFile(testApk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                .mapNotNull { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }
                        .takeIf { it.toString(Charsets.ISO_8859_1).contains(descriptor) }
                }
                .firstOrNull()
                ?: error("测试 APK 的 dex 中找不到 ${Probe::class.java.name}")
        }
        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("classes.dex"))
                zip.write(dex)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("META-INF/khatkit-dependency.properties"))
                zip.write("entry=${Probe::class.java.name}\nname=wxProbe\nversion=1.0.0\n".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun downloadedArtifactIsReadOnlyAndLoadsOnDevice() = runBlocking {
        val bytes = dependencyJarBytes()
        val req = CardManifest.DependencyReq(
            name = "wxProbe",
            version = "1.0.0",
            sha256 = sha256(bytes),
        )
        val manager = DependencyManager(
            context = appContext,
            hubBaseUrl = { "https://example.invalid" },
            fetchBytes = { bytes },
        )
        try {
            val result = manager.ensure(req)
            val ok = result as? DependencyEnsureResult.Ok ?: error("依赖加载失败：$result")
            assertEquals("wxProbe", ok.name)

            val artifact = File(appContext.filesDir, "dependencies/wxProbe/1.0.0.jar")
            assertTrue("产物应存在于应用私有目录：${artifact.absolutePath}", artifact.isFile)
            assertFalse("产物必须只读（Android 14+ W^X 要求）", artifact.canWrite())
            assertFalse("依赖目录不应可写", artifact.parentFile!!.canWrite())
            assertEquals(sha256(bytes), ok.sha256)

            // 复用只读产物：再次 ensure 不得重写，直接命中缓存
            val again = manager.ensure(req)
            assertTrue(again is DependencyEnsureResult.Ok)
            assertFalse("复用后产物仍须只读", artifact.canWrite())
        } finally {
            deleteRecursively(File(appContext.filesDir, "dependencies/wxProbe"))
            deleteRecursively(File(appContext.codeCacheDir, "dependency-dex/wxProbe"))
        }
    }

    @Test
    fun writableDexIsBlockedByPlatform() {
        // 负向对照：证明设备确实启用了 Android 14+ 的 W^X 动态代码加载拦截
        if (Build.VERSION.SDK_INT < 34) return
        val bytes = dependencyJarBytes()
        val writable = File(appContext.cacheDir, "wxProbe-writable.jar")
        try {
            writable.writeBytes(bytes)
            assertTrue(writable.canWrite())
            val optimized = File(appContext.codeCacheDir, "wxProbe-negative").apply { mkdirs() }
            val failure = runCatching {
                val loader = DexClassLoader(writable.absolutePath, optimized.absolutePath, null, appContext.classLoader)
                Class.forName(Probe::class.java.name, true, loader)
            }.exceptionOrNull()
            assertTrue(
                "可写 dex 应被系统拦截（SecurityException），实际：$failure",
                failure is SecurityException,
            )
        } finally {
            writable.setWritable(true, true)
            writable.delete()
            deleteRecursively(File(appContext.codeCacheDir, "wxProbe-negative"))
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.setWritable(true, true)
        file.delete()
    }
}
