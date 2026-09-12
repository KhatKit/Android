package heizige.kk.khatkit.bridge.impl

import android.content.Context
import heizige.kk.khatkit.bridge.StoreBridge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * store bridge 的宿主实现（设计文档 7.4）。
 *
 * - KV：SharedPreferences，底层 key 自动带 `card_<name>_` 前缀
 * - 文件：filesDir/khatkit/store/<name>/files
 * - 结构化：db/<table>.jsonl，按行追加
 * - 密钥：Keystore 加密，独立于 kv
 * - export/import：换机备份（默认不含密钥）
 */
class FileStoreBridge(
    context: Context,
    private val cardName: String,
    quotaMb: Int = 50,
) : StoreBridge {

    private val quotaBytes: Long = quotaMb.coerceAtLeast(1).toLong() * 1024L * 1024L
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("khatkit_card_$cardName", Context.MODE_PRIVATE)
    private val fileDir = File(appContext.filesDir, "khatkit/store/$cardName/files").apply { mkdirs() }
    private val dbDir = File(appContext.filesDir, "khatkit/store/$cardName/db").apply { mkdirs() }
    private val secrets = SecretStore(appContext, cardName)
    private val shared = SharedStore(File(appContext.filesDir, "khatkit/shared"), cardName)
    private val json = Json { ignoreUnknownKeys = true }

    override fun kvGet(key: String, default: String?): String? = prefs.getString(prefKey(key), default)

    override fun kvSet(key: String, value: String) {
        prefs.edit().putString(prefKey(key), value).apply()
    }

    override fun fileRead(name: String): String? =
        safeFile(name)?.takeIf { it.exists() }?.readText()

    override fun fileWrite(name: String, content: String) {
        val target = safeFile(name) ?: error("非法文件名：$name")
        enforceQuota(content.toByteArray(Charsets.UTF_8).size.toLong())
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    override fun dbQuery(table: String, where: String, args: List<Any?>): List<Map<String, Any?>> {
        val predicate = WhereParser.compile(where, args)
        return readRows(table).filter(predicate)
    }

    override fun dbInsert(table: String, row: Map<String, Any?>) {
        val target = safeDbFile(table) ?: error("非法表名：$table")
        val line = json.encodeToString(JsonElement.serializer(), toJson(row)) + "\n"
        enforceQuota(line.toByteArray(Charsets.UTF_8).size.toLong())
        target.parentFile?.mkdirs()
        target.appendText(line)
    }

    /** 配额是天花板：file + db 合计超过 manifest 的 quota_mb 时拒绝写入。 */
    private fun enforceQuota(additionalBytes: Long) {
        val used = directorySize(fileDir) + directorySize(dbDir)
        require(used + additionalBytes <= quotaBytes) {
            "store 配额超限：已用 ${used / 1024 / 1024}MB，本次 ${
                (additionalBytes / 1024).coerceAtLeast(1)
            }KB，上限 ${quotaBytes / 1024 / 1024}MB"
        }
    }

    private fun directorySize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    override fun secretGet(key: String): String? = secrets.get(key)

    override fun secretSet(key: String, value: String) = secrets.set(key, value)

    override fun sharedWrite(name: String, content: String) = shared.write(name, content)

    override fun sharedRead(name: String): String? = shared.read(name)

    override fun sharedList(): List<String> = shared.list()

    override fun sharedDelete(name: String): Boolean = shared.delete(name)

    /** 供 UI/卡片查询「该卡片存了哪些密钥」，便于撤销。 */
    fun secretList(): List<String> = secrets.names()

    fun secretRemove(key: String) = secrets.remove(key)

    /** 导出为 zip；密钥默认不导出（Keystore 不随备份走）。 */
    fun export(includeSecrets: Boolean = false): String {
        val target = File(appContext.cacheDir, "khatkit-export-$cardName.zip")
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            prefs.all.forEach { (key, value) ->
                if (value is String) writeEntry(zip, "kv/${encodePath(key)}.txt", value)
            }
            fileDir.walkTopDown().filter { it.isFile }.forEach { file ->
                writeEntry(zip, "files/${file.relativeTo(fileDir).path}", file.readText())
            }
            dbDir.walkTopDown().filter { it.isFile }.forEach { file ->
                writeEntry(zip, "db/${file.name}", file.readText())
            }
        }
        return target.absolutePath
    }

    fun import(path: String) {
        ZipInputStream(File(path).inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val bytes = zip.readBytes()
                when {
                    name.startsWith("kv/") && name.endsWith(".txt") -> {
                        val key = name.removePrefix("kv/").removeSuffix(".txt")
                        prefs.edit().putString(prefKey(decodePath(key)), bytes.toString(Charsets.UTF_8)).apply()
                    }
                    name.startsWith("files/") -> {
                        safeFile(name.removePrefix("files/"))?.let { file ->
                            file.parentFile?.mkdirs()
                            file.writeBytes(bytes)
                        }
                    }
                    name.startsWith("db/") -> {
                        safeDbFile(name.removePrefix("db/").removeSuffix(".jsonl"))?.let { file ->
                            file.parentFile?.mkdirs()
                            file.writeBytes(bytes)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun prefKey(key: String): String = "card_${cardName}_$key"

    private fun safeFile(name: String): File? {
        if (name.isBlank()) return null
        val base = fileDir.canonicalFile
        val target = File(base, name).canonicalFile
        return target.takeIf { it.path.startsWith(base.path) }
    }

    private fun safeDbFile(table: String): File? {
        if (!table.matches(Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$"))) return null
        val base = dbDir.canonicalFile
        val target = File(base, "$table.jsonl").canonicalFile
        return target.takeIf { it.path.startsWith(base.path) }
    }

    private fun readRows(table: String): List<Map<String, Any?>> {
        val target = safeDbFile(table) ?: return emptyList()
        if (!target.exists()) return emptyList()
        return target.readLines().mapNotNull { line ->
            if (line.isBlank()) null
            else runCatching {
                (json.parseToJsonElement(line) as? JsonObject)?.mapValues { fromJson(it.value) }
            }.getOrNull()
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun encodePath(path: String): String = path.replace("%", "%25").replace("/", "%2F")

    private fun decodePath(path: String): String = path.replace("%2F", "/").replace("%25", "%")

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map { toJson(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun fromJson(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" || element.content == "false" -> element.content.toBoolean()
            element.content.contains('.') -> element.content.toDoubleOrNull()
            else -> element.content.toLongOrNull() ?: element.content
        }
        is JsonArray -> element.map { fromJson(it) }
        is JsonObject -> element.mapValues { fromJson(it.value) }
    }
}

/** 极简 where 解析：`col op ? [AND col op ?]*`，op ∈ = != <> > < >= <= like。 */
internal object WhereParser {

    private val CLAUSE = Regex(
        "^([A-Za-z_][A-Za-z0-9_]*)\\s*(=|!=|<>|>=|<=|>|<|like)\\s*\\?$",
        RegexOption.IGNORE_CASE,
    )

    fun compile(where: String, args: List<Any?>): (Map<String, Any?>) -> Boolean {
        if (where.isBlank()) return { true }
        val argIterator = args.iterator()
        val predicates = where.split(Regex("(?i)\\s+and\\s+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { clause ->
                val match = CLAUSE.find(clause) ?: error("不支持的 where 子句：$clause")
                val column = match.groupValues[1]
                val op = match.groupValues[2].lowercase()
                val expected = if (argIterator.hasNext()) argIterator.next() else null
                { row: Map<String, Any?> -> compare(row[column], expected, op) }
            }
        return { row -> predicates.all { predicate -> predicate(row) } }
    }

    private fun compare(actual: Any?, expected: Any?, op: String): Boolean {
        if (actual == null || expected == null) {
            return when (op) {
                "=" -> actual == expected
                "!=", "<>" -> actual != expected
                else -> false
            }
        }
        val actualNumber = actual.toString().toDoubleOrNull()
        val expectedNumber = expected.toString().toDoubleOrNull()
        if (actualNumber != null && expectedNumber != null) {
            return when (op) {
                "=" -> actualNumber == expectedNumber
                "!=", "<>" -> actualNumber != expectedNumber
                ">" -> actualNumber > expectedNumber
                "<" -> actualNumber < expectedNumber
                ">=" -> actualNumber >= expectedNumber
                "<=" -> actualNumber <= expectedNumber
                "like" -> actual.toString().contains(expected.toString(), ignoreCase = true)
                else -> false
            }
        }
        val a = actual.toString()
        val e = expected.toString()
        return when (op) {
            "=" -> a == e
            "!=", "<>" -> a != e
            ">" -> a > e
            "<" -> a < e
            ">=" -> a >= e
            "<=" -> a <= e
            "like" -> a.contains(e, ignoreCase = true)
            else -> false
        }
    }
}
