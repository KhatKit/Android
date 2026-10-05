package heizige.kk.khatkit.app.core.data.ai.tavern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `TavernChatCodec` 的**消息解码**护栏：`swipes` / `swipe_id` / `is_system` / `is_user`
 * → [MessageNode] 这一段解析全仓只允许有一份实现。
 *
 * ## 这条护栏防的是什么
 *
 * 酒馆聊天文件的消息解析有三个入口，全部读同一批字段（`mes` / `swipes` / `swipe_id` /
 * `is_user` / `is_system`）：
 *
 * | 入口 | 文件形态 | 消费者 |
 * |---|---|---|
 * | [TavernChatCodec.import] 的数组分支 | `[header, msg, …]` | [TavernChatCodec.importGroup]（群聊导入） |
 * | [TavernChatCodec.import] 的 JSONL 分支 → `importLines` → `importObjects` | 每行一个对象 | 单聊导入 / `exportJsonl` 往返 |
 * | [TavernChatCodec.import] 的包装分支 → `importObjects` | `{"messages":[…]}` | 单聊导入 |
 *
 * 三条入口必须解出**逐字相同**的 [MessageNode]：同一个 `role`、同一批 swipe、同一个
 * `selectIndex`。一旦解析有两份实现，任何一侧改了 role 判定或 `swipe_id` 夹取都不会波及
 * 另一侧，而**没有任何编译期信号** —— 表现是「同一份酒馆文件，从群聊入口导入和从单聊入口
 * 导入得到不同的消息」。
 *
 * 这个副本真的存在过：`import` 的数组分支曾逐字抄了 `documentFrom` 的整个 map 块，而群聊
 * 导入（`importGroup` → `import`）读的是**数组分支**、单聊读的是 `documentFrom`，也就是
 * 漂移会直接劈开群聊与单聊两侧。现已收敛到 `documentFrom` 一份。
 *
 * 纯函数/行为测试证明不了「只有一份实现」——两份都能算对，测试照样全绿。要钉住「重复」这
 * 件事本身，只能在源码层数出现次数，故用文本护栏（与 `ChatServiceSenderNameGuardTest`、
 * `ConversationTypeFilterSourceGuardTest` 同一取舍）。
 *
 * 分工：
 * - [swipeAndRoleDecoding_isImplementedExactlyOnce_inAllMainSources] —— 结构层：解析骨架不允许有第二份
 * - [allImportShapes_delegateToTheSharedDecoder] —— 三个入口都收敛到同一个解码器
 */
class TavernChatMessageDecodeGuardTest {

    // ---------- 1. 结构层：解码骨架全仓只有一份 ----------

    /**
     * 匹配「按 `is_system` 布尔字段判定 `MessageRole`」这一行 —— 正是解码骨架的支点。
     *
     * 故意选一个**读**而不是**写**的形状：本文件另有三处写 `is_system`
     * （`export` 的 `updated["is_system"] = …`、`groupMessageOf` 的 `put("is_system", …)`、
     * 文档注释），它们都是赋值形状，本正则要求后面跟 `?.jsonPrimitive?.booleanOrNull`，
     * 因此不会误伤。
     */
    @Test
    fun swipeAndRoleDecoding_isImplementedExactlyOnce_inAllMainSources() {
        val hits = mainSourceFiles()
            .flatMap { file ->
                val code = code(file.readText())
                DECODE_SHAPE.findAll(code).map { match ->
                    file to match.range.first.lineNumberIn(code)
                }
            }
            .toList()

        assertEquals(
            "酒馆消息解码（`is_system` → MessageRole 判定）只允许在 $SHARED_DECODER_FILE " +
                "一份里实现。请把其它地方改成调用共享解码器 documentFrom(header, messages)，" +
                "不要就地再抄一份 map —— 抄出来的副本不会被另一侧的修复覆盖，" +
                "同一份酒馆文件就会按入口不同解出不同的 role / selectIndex。" +
                "实际命中 ${hits.map { (f, line) -> f.relativeTo(repoRoot()).path + ":" + line }}",
            1,
            hits.size,
        )
        assertEquals(
            "$SHARED_DECODER_FILE 是唯一允许实现解码的文件",
            SHARED_DECODER_FILE,
            hits.single().first.relativeTo(repoRoot()).path,
        )
    }

    // ---------- 2. 三个入口都收敛到同一个解码器 ----------

    @Test
    fun allImportShapes_delegateToTheSharedDecoder() {
        val source = code(File(repoRoot(), SHARED_DECODER_FILE).readText())

        // 数组分支（群聊导入走这条）：`documentFrom(header, messages.map { it.jsonObject })`。
        assertTrue(
            "$SHARED_DECODER_FILE 的 import() 数组分支必须调 documentFrom(" +
                "，不能自己内联一份消息解码 —— importGroup 读的就是这条分支。",
            source.contains("documentFrom(header, messages.map { it.jsonObject })"),
        )

        // 包装 / JSONL 两条分支：经 importObjects 落到同一个解码器。
        assertTrue(
            "$SHARED_DECODER_FILE 的 importObjects() 必须调 documentFrom(" +
                "，JSONL（importLines）与 {\"messages\":[…]} 两条入口都由它解码。",
            source.contains("return documentFrom(header, messages)"),
        )
        assertTrue(
            "$SHARED_DECODER_FILE 的 importLines() 必须复用 importObjects(" +
                "，不要自己再解一遍消息。",
            IMPORT_LINES_DELEGATES.containsMatchIn(source),
        )

        // 唯一实现处本身必须在，别把共享解码器删了。
        assertEquals(
            "$SHARED_DECODER_FILE 里共享解码器 documentFrom( 应恰好有一处定义",
            1,
            DECODER_DECLARATION.findAll(source).count(),
        )
    }

    companion object {
        private const val SHARED_DECODER_FILE =
            "app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernChatCodec.kt"

        /** 「按 `is_system` 布尔字段判定 role」—— 解码骨架的支点，只可能是**读**。 */
        private val DECODE_SHAPE =
            Regex("""\["is_system"\]\s*\?\s*\.\s*jsonPrimitive\s*\?\s*\.\s*booleanOrNull""")

        /** `private fun documentFrom(` 的定义处，允许任意缩进。 */
        private val DECODER_DECLARATION =
            Regex("""private\s+fun\s+documentFrom\s*\(""")

        /** `importLines` 里把解析结果交回 `importObjects`。 */
        private val IMPORT_LINES_DELEGATES =
            Regex("""return\s+importObjects\s*\(""")

        private val SKIP_DIRS = setOf(
            "build", ".git", ".gradle", ".idea", "node_modules", "out", ".kotlin",
        )

        /**
         * 去掉注释，只留代码，**并保持行数不变**。
         *
         * 收敛那笔改动在 `import()` 上方留了一段注释复述旧写法（`swipes` / `swipe_id` /
         * `is_system` 这些字面量都在注释里），不剥注释时它会被当成真命中 —— 护栏因为解释
         * 它自己而变红，只好逼着人把解释删掉。剥掉注释后，注释可以放心写全。
         *
         * 块注释用等量空格回填而不是折成一行：否则失败信息里那个 `range.first + 1` 报出来的
         * 行号会与真实源码错位，指路指到别处去。
         */
        private fun code(source: String): String = source
            .replace(BLOCK_COMMENT) { m -> m.value.replace(NON_NEWLINE, " ") }
            .replace(LINE_COMMENT, " ")

        private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val LINE_COMMENT = Regex("""//[^\n]*""")
        private val NON_NEWLINE = Regex("""[^\n]""")

        /**
         * 字符偏移 → 1 基行号。
         *
         * `MatchResult.range` 给的是**字符偏移**而不是行号，直接 +1 会报出几千这种
         * 离谱数字，指路指到天上去。失败信息是这条护栏唯一的解释手段，得能照着改。
         */
        private fun Int.lineNumberIn(source: String): Int =
            source.take(this).count { it == '\n' } + 1

        /**
         * 从工作目录向上找带 `settings.gradle.kts` 的那层当仓库根。
         *
         * 不写死 `..`：Gradle JVM 单测的工作目录是模块目录（`:app`），但那是个约定而非
         * 契约，约定变了整条护栏会静默读错文件路径退化成「什么都没检查」。
         */
        private fun repoRoot(): File {
            var dir = File("").absoluteFile
            while (true) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile ?: error("找不到仓库根（向上没有 settings.gradle.kts）")
            }
        }

        /** 全仓所有 `src/main` 下的 Kotlin 源码（不碰 `src/test` / `src/androidTest`）。 */
        private fun mainSourceFiles(): List<File> {
            val out = mutableListOf<File>()
            fun walk(dir: File) {
                for (child in dir.listFiles() ?: return) {
                    if (!child.isDirectory) continue
                    if (child.name in SKIP_DIRS) continue
                    if (child.name == "src") {
                        // 命中源码集就不再往里钻 `src/test` / `src/androidTest`，省掉整棵树的遍历。
                        File(child, "main").takeIf { it.isDirectory }
                            ?.walkTopDown()
                            ?.filter { it.isFile && it.extension == "kt" }
                            ?.forEach { out += it }
                    } else {
                        walk(child)
                    }
                }
            }
            walk(repoRoot())
            return out
        }
    }
}
