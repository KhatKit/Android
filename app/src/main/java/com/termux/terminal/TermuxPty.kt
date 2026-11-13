package com.termux.terminal

import android.os.ParcelFileDescriptor
import java.io.Closeable

/** Small bridge that exposes the PTY created by Termux's existing native backend. */
class TermuxPty(
    shellPath: String,
    cwd: String,
    args: Array<String>,
    environment: Array<String>,
    rows: Int,
    cols: Int,
) : Closeable {
    private val pidHolder = IntArray(1)
    private val descriptor: Int = JNI.createSubprocess(
        shellPath,
        cwd,
        args,
        environment,
        pidHolder,
        cols,
        rows,
    ).also { check(it >= 0) { "Unable to create terminal PTY: $it" } }

    private val master = ParcelFileDescriptor.adoptFd(descriptor)
    val input = ParcelFileDescriptor.AutoCloseInputStream(
        ParcelFileDescriptor.dup(master.fileDescriptor),
    )
    val output = ParcelFileDescriptor.AutoCloseOutputStream(
        ParcelFileDescriptor.dup(master.fileDescriptor),
    )

    fun resize(rows: Int, cols: Int) {
        JNI.setPtyWindowSize(descriptor, cols, rows)
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { master.close() }
    }
}
