package heizige.kk.khatkit.dependencies.imageToolbox

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

internal object ToolboxWorkflow {
    val processOps = setOf("lut_cube", "filter_chain")
    val multiOps = setOf("split_grid", "split_horizontal", "split_vertical", "compare", "difference")

    fun process(source: Bitmap, op: String, params: Map<String, Any?>): Bitmap = when (op) {
        "lut_cube" -> {
            val path = ToolboxParams.str(params, "lut", "")
            require(path.isNotBlank()) { "lut_cube 需要本地 .cube 文件路径 lut" }
            ToolboxIO.requireSharedStorageAccess(path)
            val file = File(path)
            require(file.isFile && file.length() <= 16 * 1024 * 1024) { "LUT 文件不存在或超过 16 MiB" }
            val lut = CubeLut.parse(file.readText())
            val strength = ToolboxParams.num(params, "strength", 1.0)
            require(strength.isFinite() && strength in 0.0..1.0) { "strength 必须为 0..1" }
            val pixels = pixels(source)
            for (index in pixels.indices) pixels[index] = lut.apply(pixels[index], strength)
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        }
        "filter_chain" -> chain(source, params)
        else -> error("不支持的流程操作：$op")
    }

    private fun chain(source: Bitmap, params: Map<String, Any?>): Bitmap {
        val rawSteps = params["steps"] as? List<*> ?: error("filter_chain 需要 steps 数组")
        require(rawSteps.size in 1..32) { "滤镜链需要 1..32 个步骤" }
        val steps = rawSteps.map { raw ->
            val step = raw as? Map<*, *> ?: error("每个步骤必须为对象")
            val op = step["op"] as? String ?: error("步骤缺少 op")
            require(op != "filter_chain") { "不允许嵌套滤镜链" }
            val arguments = step["params"]
            require(arguments == null || arguments is Map<*, *>) { "步骤 params 必须为对象" }
            val values = arguments?.entries?.associate { (key, value) ->
                require(key is String)
                key to value
            }.orEmpty()
            op to values
        }
        var current = source
        try {
            steps.forEach { (op, values) ->
                val next = when {
                    op == "lut_cube" -> process(current, op, values)
                    ToolboxFilter.isPreset(op) -> ToolboxFilter.preset(current, op)
                    ToolboxGeometry.isGeometryOp(op) -> ToolboxGeometry.apply(current, op, values)
                    else -> ToolboxFilter.apply(current, op, values)
                }
                if (current !== source && next !== current) current.recycle()
                current = next
            }
            return current
        } catch (error: Throwable) {
            if (current !== source && !current.isRecycled) current.recycle()
            throw error
        }
    }

    fun multi(op: String, inputs: List<String>, params: Map<String, Any?>): String = when (op) {
        "split_grid", "split_horizontal", "split_vertical" -> split(op, inputs, params)
        "compare", "difference" -> compare(op, inputs, params)
        else -> error("不支持的多图操作：$op")
    }

    private fun split(op: String, inputs: List<String>, params: Map<String, Any?>): String {
        require(inputs.size == 1) { "图片切片每次需要一张图片" }
        val source = ToolboxIO.decode(inputs.single())
        try {
            val count = ToolboxParams.int(params, "count", 2)
            val columns = when (op) {
                "split_horizontal" -> count
                "split_vertical" -> 1
                else -> ToolboxParams.int(params, "columns", 3)
            }
            val rows = when (op) {
                "split_horizontal" -> 1
                "split_vertical" -> count
                else -> ToolboxParams.int(params, "rows", 3)
            }
            val tiles = RasterTools.tiles(source.width, source.height, columns, rows)
            val input = File(inputs.single())
            val directory = File(ToolboxParams.str(params, "output_dir",
                ToolboxParams.str(params, "output", File(input.parentFile, "${input.nameWithoutExtension}_tiles").path)))
            ToolboxIO.requireSharedStorageAccess(directory.absolutePath)
            val format = ToolboxIO.normalizeFormat(ToolboxParams.str(params, "format", "png"))
            val targets = tiles.indices.map { index ->
                File(directory, "${input.nameWithoutExtension}_r${index / columns + 1}_c${index % columns + 1}.$format")
            }
            require(targets.none { it.exists() }) { "切片文件已存在，请指定新的 output_dir，避免覆盖" }
            require(directory.isDirectory || directory.mkdirs()) { "无法创建切片输出目录" }
            val outputs = JSONArray()
            tiles.forEachIndexed { index, tile ->
                val bitmap = Bitmap.createBitmap(source, tile.x, tile.y, tile.width, tile.height)
                try {
                    outputs.put(ToolboxIO.save(bitmap, targets[index], ToolboxParams.int(params, "quality", 92)))
                } finally {
                    if (bitmap !== source) bitmap.recycle()
                }
            }
            return JSONObject().put("outputs", outputs).put("rows", rows).put("columns", columns)
                .put("width", source.width).put("height", source.height).toString()
        } finally {
            source.recycle()
        }
    }

    private fun compare(op: String, inputs: List<String>, params: Map<String, Any?>): String {
        require(inputs.size == 2) { "图片比较需要且仅需要两张图片" }
        val first = ToolboxIO.decode(inputs[0])
        try {
            val second = ToolboxIO.decode(inputs[1])
            try {
                require(first.width == second.width && first.height == second.height) { "比较图片尺寸必须相同，不会自动缩放" }
                val left = pixels(first)
                val right = pixels(second)
                val threshold = ToolboxParams.int(params, "threshold", 0)
                val stats = RasterTools.compare(left, right, threshold)
                val result = JSONObject().put("width", first.width).put("height", first.height)
                    .put("threshold", threshold).put("changed_pixels", stats.changedPixels)
                    .put("changed_ratio", stats.changedPixels.toDouble() / left.size)
                    .put("mae_rgb", stats.mae).put("mse_rgb", stats.mse)
                    .put("psnr_rgb", stats.psnr ?: "infinity")
                    .put("identical", left.contentEquals(right))
                if (op == "difference") {
                    val target = ToolboxIO.outputFor(inputs[0], "difference", "png", ToolboxParams.str(params, "output", ""))
                    require(inputs.none { File(it).canonicalFile == target.canonicalFile }) { "差异图不能覆盖输入图片" }
                    val gain = ToolboxParams.num(params, "gain", 1.0)
                    require(gain.isFinite() && gain in 1.0..32.0) { "gain 必须为 1..32" }
                    for (index in left.indices) {
                        var color = -0x1000000
                        for (shift in 16 downTo 0 step 8) {
                            val delta = abs(((left[index] ushr shift) and 255) - ((right[index] ushr shift) and 255))
                            color = color or ((delta * gain).toInt().coerceAtMost(255) shl shift)
                        }
                        left[index] = color
                    }
                    val bitmap = Bitmap.createBitmap(left, first.width, first.height, Bitmap.Config.ARGB_8888)
                    result.put("output", ToolboxIO.saveAndRecycle(bitmap, target))
                }
                return result.toString()
            } finally {
                second.recycle()
            }
        } finally {
            first.recycle()
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}
