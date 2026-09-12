package heizige.kk.khatkit.bridge.impl

import java.io.File

/**
 * 卡片间共享区（设计文档 15.2 第 11 问）。
 *
 * 隔离策略下卡片 A 的产物默认对 B 不可见；需要工作流协作时，
 * 显式写到这里：**写只进自己的命名空间，读可以跨卡片按名取**，
 * 删除只能删自己的。这样既保持隔离，又能把上一步产物交给下一步。
 */
class SharedStore(
    private val rootDir: File,
    private val owner: String,
) {
    fun write(name: String, content: String) {
        val target = ownerFile(name) ?: throw IllegalArgumentException("非法共享文件名：$name")
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    /** 优先按显式路径（`<owner>/<name>`）读；否则在所有卡片目录里按文件名找。 */
    fun read(name: String): String? {
        directFile(name)?.takeIf { it.isFile }?.let { return it.readText() }
        if ('/' !in name) {
            return findByFileName(name)?.readText()
        }
        return null
    }

    fun list(): List<String> {
        if (!rootDir.exists()) return emptyList()
        return rootDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(rootDir).path.replace(File.separatorChar, '/') }
            .sorted()
            .toList()
    }

    /** 只能删自己命名空间里的文件。 */
    fun delete(name: String): Boolean = ownerFile(name)?.takeIf { it.isFile }?.delete() ?: false

    fun exists(name: String): Boolean = directFile(name)?.isFile == true || findByFileName(name) != null

    private fun ownerFile(name: String): File? = resolveWithin(File(rootDir, owner), name)

    private fun directFile(name: String): File? = resolveWithin(rootDir, name)

    private fun resolveWithin(baseDir: File, relative: String): File? {
        if (relative.isBlank()) return null
        val base = baseDir.canonicalFile
        val target = File(base, relative).canonicalFile
        return target.takeIf { it.path == base.path || it.path.startsWith(base.path + File.separator) }
    }

    private fun findByFileName(name: String): File? {
        if (!rootDir.exists()) return null
        return rootDir.walkTopDown()
            .filter { it.isFile && it.name == name }
            .maxByOrNull { it.lastModified() }
    }
}
