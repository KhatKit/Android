package heizige.kk.khatkit.app.feature.extensions

import android.content.Context
import com.johnan.terminal.core.ArrowDirection
import com.johnan.terminal.core.TerminalEmulator
import com.johnan.terminal.core.terminalConfig
import com.termux.terminal.TermuxPty
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Bridges the standalone shell process to the Compose terminal emulator. */
class StandaloneShellTerminal(context: Context) : Closeable {
    val emulator = TerminalEmulator(
        config = terminalConfig {
            initialRows = 24
            initialCols = 80
            maxScrollback = 2_000
        },
    )

    private val pty = TermuxPty(
        shellPath = "/system/bin/sh",
        cwd = context.filesDir.absolutePath,
        args = arrayOf("-i"),
        environment = arrayOf(
            "HOME=${context.filesDir.absolutePath}",
            "PATH=/system/bin:/system/xbin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PS1=\$ ",
            "PS2=> ",
        ),
        rows = 24,
        cols = 80,
    )
    private val output: OutputStream = pty.output
    private val closed = AtomicBoolean(false)
    private val emulatorLock = Any()
    private var initialSizeSynchronized = false
    private val reader = Thread({ readOutput() }, "khatkit-terminal-output").apply {
        isDaemon = true
        start()
    }

    fun write(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    fun write(bytes: ByteArray) {
        if (closed.get()) return
        synchronized(output) {
            runCatching {
                output.write(bytes)
                output.flush()
            }
        }
    }

    fun writeArrow(direction: ArrowDirection, shift: Boolean) {
        val suffix = when (direction) {
            ArrowDirection.UP -> "A"
            ArrowDirection.DOWN -> "B"
            ArrowDirection.RIGHT -> "C"
            ArrowDirection.LEFT -> "D"
        }
        write("\u001B[${if (shift) "1;2" else ""}$suffix")
    }

    fun resize(rows: Int, cols: Int) {
        synchronized(emulatorLock) {
            emulator.resizeSync(rows, cols)
            pty.resize(rows, cols)
            if (!initialSizeSynchronized) {
                initialSizeSynchronized = true
                write("\u000C")
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pty.close()
        reader.interrupt()
    }

    private fun readOutput() {
        val input = pty.input
        val buffer = ByteArray(8 * 1024)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) {
                    synchronized(emulatorLock) {
                        emulator.processOutputSync(buffer, count)
                    }
                }
            }
        } catch (_: Exception) {
            // Closing the PTY interrupts the blocking read during normal disposal.
        } finally {
            runCatching { input.close() }
        }
    }
}
