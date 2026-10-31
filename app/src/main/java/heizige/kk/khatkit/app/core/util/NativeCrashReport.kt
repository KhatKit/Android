package heizige.kk.khatkit.app.core.util

/**
 * 原生崩溃（SIGSEGV / abort 等）的报告。
 *
 * Java 的 UncaughtExceptionHandler 接不到原生崩溃，进程直接被系统杀掉，应用里什么都留不下。
 * 下次启动时从 ApplicationExitInfo 取回系统记下的 tombstone（Android 12+ 是 protobuf），
 * 这里把它解成能读的文本：信号、原因、崩溃线程、折叠过重复帧的调用栈、崩溃前的警告/错误日志。
 *
 * 只解需要的几个字段，格式见 AOSP `system/core/debuggerd/proto/tombstone.proto`。
 */
internal data class NativeCrash(
    val signal: String,
    val cause: String,
    val threadName: String,
    val text: String,
)

internal object Tombstone {
    private const val MAX_FRAMES = 64
    private const val MAX_LOGS = 40
    private const val PRIORITY_WARN = 5

    private class Frame(val pc: Long, val function: String, val offset: Long, val file: String) {
        val key get() = "$file|$function|$offset"
    }

    private class Thread(val id: Int, val name: String, val frames: List<Frame>)

    private class Log(val time: String, val priority: Int, val tag: String, val message: String)

    fun parse(bytes: ByteArray): NativeCrash? = runCatching { decode(ProtoReader(bytes)) }.getOrNull()

    private fun decode(r: ProtoReader): NativeCrash {
        var fingerprint = ""
        var timestamp = ""
        var pid = 0
        var tid = 0
        val command = ArrayList<String>()
        var signalName = ""
        var signalCode = ""
        var faultAddress: Long? = null
        var abort = ""
        val causes = ArrayList<String>()
        val threads = HashMap<Int, Thread>()
        val logs = ArrayList<Log>()
        while (r.hasMore()) {
            val tag = r.tag()
            when (tag ushr 3) {
                2 -> fingerprint = r.string()
                4 -> timestamp = r.string()
                5 -> pid = r.varint().toInt()
                6 -> tid = r.varint().toInt()
                9 -> command += r.string()
                10 -> r.message { s ->
                    while (s.hasMore()) {
                        val t = s.tag()
                        when (t ushr 3) {
                            2 -> signalName = s.string()
                            4 -> signalCode = s.string()
                            9 -> faultAddress = s.varint()
                            else -> s.skip(t)
                        }
                    }
                }
                14 -> abort = r.string()
                15 -> r.message { s ->
                    while (s.hasMore()) {
                        val t = s.tag()
                        if (t ushr 3 == 1) causes += s.string() else s.skip(t)
                    }
                }
                16 -> r.message { entry ->
                    while (entry.hasMore()) {
                        val t = entry.tag()
                        if (t ushr 3 == 2) entry.message { readThread(it)?.let { th -> threads[th.id] = th } } else entry.skip(t)
                    }
                }
                18 -> r.message { buffer ->
                    while (buffer.hasMore()) {
                        val t = buffer.tag()
                        if (t ushr 3 == 2) buffer.message { readLog(it)?.let(logs::add) } else buffer.skip(t)
                    }
                }
                else -> r.skip(tag)
            }
        }
        val thread = threads[tid]
        val threadName = thread?.name?.ifBlank { null } ?: "tid $tid"
        val signal = listOf(signalName, signalCode).filter { it.isNotBlank() }.joinToString(" / ")
        val cause = causes.firstOrNull().orEmpty().ifBlank { abort.lineSequence().firstOrNull().orEmpty() }
        val text = buildString {
            appendLine("Native crash")
            if (timestamp.isNotBlank()) appendLine("Time: $timestamp")
            appendLine("Process: ${command.joinToString(" ").ifBlank { "?" }} (pid $pid)")
            appendLine("Thread: $threadName (tid $tid)")
            if (signal.isNotBlank()) {
                append("Signal: $signal")
                faultAddress?.let { append(", fault addr 0x${java.lang.Long.toHexString(it)}") }
                appendLine()
            }
            causes.forEach { appendLine("Cause: $it") }
            if (abort.isNotBlank()) appendLine("Abort: $abort")
            if (fingerprint.isNotBlank()) appendLine("Build: $fingerprint")
            if (thread != null && thread.frames.isNotEmpty()) {
                appendLine()
                appendLine("Backtrace (${thread.frames.size} frames):")
                append(formatFrames(thread.frames))
            }
            val important = logs.filter { it.priority >= PRIORITY_WARN }.takeLast(MAX_LOGS)
            if (important.isNotEmpty()) {
                appendLine()
                appendLine("Recent logs (warn and above):")
                important.forEach { appendLine("${it.time} ${priorityLetter(it.priority)} ${it.tag}: ${redact(it.message)}") }
            }
        }
        return NativeCrash(signal = signal, cause = cause, threadName = threadName, text = text)
    }

    private fun readThread(r: ProtoReader): Thread? {
        var id = 0
        var name = ""
        val frames = ArrayList<Frame>()
        while (r.hasMore()) {
            val tag = r.tag()
            when (tag ushr 3) {
                1 -> id = r.varint().toInt()
                2 -> name = r.string()
                4 -> r.message { readFrame(it)?.let(frames::add) }
                else -> r.skip(tag)
            }
        }
        return Thread(id, name, frames)
    }

    private fun readFrame(r: ProtoReader): Frame? {
        var relPc = 0L
        var function = ""
        var offset = 0L
        var file = ""
        while (r.hasMore()) {
            val tag = r.tag()
            when (tag ushr 3) {
                1 -> relPc = r.varint()
                4 -> function = r.string()
                5 -> offset = r.varint()
                6 -> file = r.string()
                else -> r.skip(tag)
            }
        }
        return Frame(relPc, function, offset, file)
    }

    private fun readLog(r: ProtoReader): Log? {
        var time = ""
        var priority = 0
        var tag = ""
        var message = ""
        while (r.hasMore()) {
            val t = r.tag()
            when (t ushr 3) {
                1 -> time = r.string()
                4 -> priority = r.varint().toInt()
                5 -> tag = r.string()
                6 -> message = r.string()
                else -> r.skip(t)
            }
        }
        return Log(time, priority, tag, message.trimEnd())
    }

    /**
     * 栈溢出的栈是几百帧同一段递归：连续重复的 1～4 帧块只留一份，后面注明重复了几次，
     * 报告里看得到是哪段在转圈，不会被几百行刷掉。
     */
    private fun formatFrames(frames: List<Frame>): String = buildString {
        var i = 0
        var printed = 0
        while (i < frames.size && printed < MAX_FRAMES) {
            var best = 1
            var bestReps = 1
            for (k in 1..4) {
                var reps = 1
                while (i + (reps + 1) * k <= frames.size &&
                    (0 until k).all { frames[i + reps * k + it].key == frames[i + it].key }
                ) {
                    reps++
                }
                if (reps >= 3 && reps * k > bestReps * best) {
                    best = k
                    bestReps = reps
                }
            }
            for (j in 0 until best) {
                appendLine(formatFrame(i + j, frames[i + j]))
                printed++
            }
            if (bestReps > 1) {
                appendLine("  … the ${if (best == 1) "frame" else "$best frames"} above repeat ${bestReps - 1} more times")
            }
            i += best * bestReps
        }
        if (i < frames.size) appendLine("  … ${frames.size - i} more frames")
    }

    private fun formatFrame(index: Int, f: Frame): String {
        val name = f.function.ifBlank { "?" }.let { if (it.length > 160) it.take(157) + "..." else it }
        val file = f.file.substringAfterLast('/')
        return "  #%02d pc %08x  %s (%s+%d)".format(index, f.pc, file, name, f.offset)
    }

    private fun priorityLetter(p: Int) = when (p) {
        2 -> "V"; 3 -> "D"; 4 -> "I"; 5 -> "W"; 6 -> "E"; 7 -> "F"; else -> "?"
    }

    private val secret = Regex("""(?i)(authorization|bearer|api[-_ ]?key|token)(["']?\s*[:=]?\s*)\S+""")

    /** 报告会被复制、发给别人：日志里像密钥的值一律打码。 */
    internal fun redact(message: String): String = secret.replace(message) { "${it.groupValues[1]}${it.groupValues[2]}***" }
}

/** 最小的 protobuf 读取器：只支持 tombstone 用到的 varint / 64 位 / 长度前缀 / 32 位四种线型。 */
internal class ProtoReader(private val buf: ByteArray, private var pos: Int = 0, private val end: Int = buf.size) {
    fun hasMore() = pos < end

    fun tag(): Int = varint().toInt()

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            check(pos < end) { "truncated varint" }
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        error("malformed varint")
    }

    private fun length(): Int {
        val n = varint()
        check(n in 0..(end - pos).toLong()) { "bad length" }
        return n.toInt()
    }

    fun string(): String {
        val n = length()
        return String(buf, pos, n, Charsets.UTF_8).also { pos += n }
    }

    fun <T> message(block: (ProtoReader) -> T): T {
        val n = length()
        val sub = ProtoReader(buf, pos, pos + n)
        pos += n
        return block(sub)
    }

    fun skip(tag: Int) {
        when (tag and 7) {
            0 -> varint()
            1 -> advance(8)
            2 -> advance(length())
            5 -> advance(4)
            else -> error("unsupported wire type ${tag and 7}")
        }
    }

    private fun advance(n: Int) {
        check(n <= end - pos) { "truncated" }
        pos += n
    }
}
