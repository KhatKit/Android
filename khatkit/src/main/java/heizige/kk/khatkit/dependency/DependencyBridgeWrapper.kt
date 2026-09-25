package heizige.kk.khatkit.dependency

import heizige.kk.khatkit.bridge.ImageToolboxBridge
import heizige.kk.khatkit.engine.JsonValues
import heizige.kk.khatkit.engine.ReflectiveInvoker
import kotlinx.serialization.json.JsonArray

/**
 * 动态 bridge 包装器：把 DexClassLoader 加载出来的依赖包实例包成脚本可见的 bridge。
 *
 * - 注册进 [heizige.kk.khatkit.bridge.BridgeRegistry] 后，脚本按依赖名调用；
 * - 类型化方法直接委托依赖实现（如 imageToolbox 实现 [ImageToolboxBridge]），
 *   保持 `imageToolbox.resize(...)` 既有写法不变；
 * - 通用派发 `依赖名.call("resize", "[...]")` / `依赖名.availableMethods()` 对所有依赖可用，
 *   走反射 + JSON 参数转换，依赖不必依赖任何 KhatKit 类型即可被调用。
 */
class DependencyBridgeWrapper(
    val dependencyName: String,
    private val dependency: Any,
) : KhatKitDependency, ImageToolboxBridge {

    override fun call(method: String, argsJson: String): String = try {
        JsonValues.encode(ReflectiveInvoker.invoke(dependency, method, parseArgs(argsJson)))
    } catch (e: Throwable) {
        JsonValues.encode(mapOf("__error" to (e.message ?: "依赖包调用失败：$dependencyName.$method")))
    }

    override fun availableMethods(): String = JsonValues.encode(
        ReflectiveInvoker.methodNames(dependency).filterNot { it == "call" || it == "availableMethods" }
    )

    private fun parseArgs(argsJson: String): List<Any?> {
        if (argsJson.isBlank()) return emptyList()
        val element = runCatching { JsonValues.json.parseToJsonElement(argsJson) }.getOrNull()
        val array = element as? JsonArray ?: return emptyList()
        return array.map { JsonValues.fromElement(it) }
    }

    private fun toolbox(): ImageToolboxBridge = dependency as? ImageToolboxBridge
        ?: error("依赖包 $dependencyName 未实现 ImageToolboxBridge，无法使用类型化图像方法；请改用 $dependencyName.call(...)")

    override fun resize(path: String, width: Int, height: Int, keepAspect: Boolean): String =
        toolbox().resize(path, width, height, keepAspect)

    override fun crop(path: String, x: Int, y: Int, width: Int, height: Int): String =
        toolbox().crop(path, x, y, width, height)

    override fun rotate(path: String, degrees: Float): String =
        toolbox().rotate(path, degrees)

    override fun flip(path: String, horizontal: Boolean): String =
        toolbox().flip(path, horizontal)

    override fun grayscale(path: String): String =
        toolbox().grayscale(path)

    override fun blur(path: String, radius: Int): String =
        toolbox().blur(path, radius)

    override fun sharpen(path: String, amount: Float): String =
        toolbox().sharpen(path, amount)

    override fun pixelate(path: String, blockSize: Int): String =
        toolbox().pixelate(path, blockSize)

    override fun brightnessContrast(path: String, brightness: Int, contrast: Int): String =
        toolbox().brightnessContrast(path, brightness, contrast)

    override fun saturation(path: String, factor: Float): String =
        toolbox().saturation(path, factor)

    override fun hue(path: String, degrees: Float): String =
        toolbox().hue(path, degrees)

    override fun autoContrast(path: String): String =
        toolbox().autoContrast(path)

    override fun invert(path: String): String =
        toolbox().invert(path)

    override fun sepia(path: String): String =
        toolbox().sepia(path)

    override fun watermark(
        path: String,
        text: String,
        position: String,
        alpha: Int,
        textSize: Int,
        colorHex: String,
    ): String = toolbox().watermark(path, text, position, alpha, textSize, colorHex)

    override fun border(path: String, width: Int, colorHex: String): String =
        toolbox().border(path, width, colorHex)

    override fun roundCorners(path: String, radius: Int): String =
        toolbox().roundCorners(path, radius)

    override fun convert(path: String, format: String, quality: Int): String =
        toolbox().convert(path, format, quality)

    override fun stripMetadata(path: String): String =
        toolbox().stripMetadata(path)

    override fun imagesToPdf(paths: List<String>, output: String): String =
        toolbox().imagesToPdf(paths, output)

    override fun pdfToImages(path: String, outputDir: String): List<String> =
        toolbox().pdfToImages(path, outputDir)

    override fun pdfPageCount(path: String): Int =
        toolbox().pdfPageCount(path)

    override fun process(path: String, op: String, paramsJson: String): String =
        toolbox().process(path, op, paramsJson)

    override fun compose(op: String, inputsJson: String, paramsJson: String): String =
        toolbox().compose(op, inputsJson, paramsJson)

    override fun analyze(path: String, query: String, paramsJson: String): String =
        toolbox().analyze(path, query, paramsJson)

    override fun pdfEdit(op: String, source: String, paramsJson: String): String =
        toolbox().pdfEdit(op, source, paramsJson)
}
