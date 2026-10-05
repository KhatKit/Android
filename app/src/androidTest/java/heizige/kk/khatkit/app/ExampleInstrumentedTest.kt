package heizige.kk.khatkit.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    /**
     * 断言「被测上下文确实是设备上装着的那个 app 的上下文」。
     *
     * ⚠️ **不能硬编码 applicationId**：`app/build.gradle.kts` 给 debug 变体配了
     * `applicationIdSuffix = ".debug"`，所以仪器测试里的 `targetContext.packageName`
     * 是 `heizige.kk.khatkit.debug`。写死 `heizige.kk.khatkit.app` 断言的不是被测应用，
     * 是构建变体的差异，失败时只会误导方向。
     *
     * 改成两个**互相独立**的来源对账：`Context.packageName`（ContextImpl 从包名解析出来的）
     * 与 `ApplicationInfo.packageName`（PackageManager 按已安装包给出的）。
     * 这样仍然是一条真断言——两者不一致才说明上下文拿错了 Application——
     * 但不再依赖任何写死的字符串。
     */
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse("被测上下文的包名不应为空", appContext.packageName.isBlank())
        assertEquals(
            "targetContext 必须落在设备上真实安装的那个包上（两个独立来源对账）",
            appContext.applicationInfo.packageName,
            appContext.packageName,
        )
    }
}
