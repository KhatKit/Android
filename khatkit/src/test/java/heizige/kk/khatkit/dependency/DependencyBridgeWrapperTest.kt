package heizige.kk.khatkit.dependency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动态 bridge 通用派发（不依赖 Android）：依赖包不必实现任何 KhatKit 接口，
 * 通过反射 + JSON 参数完成调用；失败收敛为 `{"__error":"..."}`。
 */
class DependencyBridgeWrapperTest {

    @Suppress("unused")
    class FakeDependency {
        fun greet(name: String): String = "你好，$name"

        fun sum(a: Int, b: Int): Int = a + b

        fun join(parts: List<String>): String = parts.joinToString("-")

        fun fail(): String = error("故意失败")
    }

    @Test
    fun genericCallDispatchesWithJsonArgs() {
        val wrapper = DependencyBridgeWrapper("demo", FakeDependency())
        assertEquals("\"你好，KhatKit\"", wrapper.call("greet", """["KhatKit"]"""))
        assertEquals("5", wrapper.call("sum", "[2,3]"))
        assertEquals("\"a-b-c\"", wrapper.call("join", """[["a","b","c"]]"""))
    }

    @Test
    fun availableMethodsListsDependencyMethods() {
        val wrapper = DependencyBridgeWrapper("demo", FakeDependency())
        val methods = wrapper.availableMethods()
        assertTrue(methods, methods.contains("\"greet\""))
        assertTrue(methods, methods.contains("\"sum\""))
        assertTrue(!methods.contains("\"call\""))
    }

    @Test
    fun unknownMethodAndFailuresBecomeChineseErrors() {
        val wrapper = DependencyBridgeWrapper("demo", FakeDependency())
        assertTrue(wrapper.call("nope", "[]").contains("__error"))
        val failed = wrapper.call("fail", "[]")
        assertTrue(failed, failed.contains("__error"))
    }

    @Test
    fun typedImageToolboxMethodsRequireImplementation() {
        val wrapper = DependencyBridgeWrapper("demo", FakeDependency())
        val error = runCatching { wrapper.grayscale("/tmp/a.png") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("未实现 ImageToolboxBridge"))
    }
}
