package heizige.kk.khatkit.app.feature.workspace.helper
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.workspace.RootfsPatchOptions
import heizige.kk.khatkit.workspace.RootfsPatcher
import heizige.kk.khatkit.workspace.WorkspaceManager
import java.io.File

internal fun createWorkspaceTerminalSession(
    context: Context,
    workspaceManager: WorkspaceManager,
    root: String,
    client: TerminalSessionClient,
    shellCompatibilityMode: Boolean,
): TerminalSession {
    val appContext = context.applicationContext
    workspaceManager.ensureWorkspace(root)
    val filesDir = workspaceManager.filesDir(root)
    val linuxDir = workspaceManager.linuxDir(root)
    val tempDir = workspaceManager.tempDir(root)
    val skillsDir = File(appContext.filesDir, FileFolders.SKILLS).apply { mkdirs() }
    val builtinSkillsDir = File(appContext.filesDir, FileFolders.BUILTIN_SKILLS).apply { mkdirs() }
    val nativeLibraryDir = File(appContext.applicationInfo.nativeLibraryDir)
    val proot = File(nativeLibraryDir, "libproot_exec.so")
    val loader = File(nativeLibraryDir, "libproot_loader.so")

    val args = mutableListOf(
        "--root-id",
        "--link2symlink",
        "--kill-on-exit",
        "-r",
        linuxDir.absolutePath,
        "-w",
        WORKSPACE_DIR,
        "-b",
        "${filesDir.absolutePath}:$WORKSPACE_DIR",
        "-b",
        "${skillsDir.absolutePath}:$SKILLS_DIR",
        "-b",
        "${builtinSkillsDir.absolutePath}:$BUILTIN_SKILLS_DIR",
    )
    listOf("/dev", "/proc", "/sys").forEach { path ->
        if (File(path).exists()) {
            args += "-b"
            args += path
        }
    }
    args += listOf(
        "/usr/bin/env",
        "-i",
        "HOME=/root",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM=xterm-256color",
        "LANG=C.UTF-8",
        "LC_ALL=C.UTF-8",
        "USER=root",
        "SHELL=/bin/bash",
        "/bin/bash",
    )

    val env = mutableListOf(
        "PROOT_LOADER=${loader.absolutePath}",
        "PROOT_TMP_DIR=${tempDir.absolutePath}",
        "TMPDIR=${tempDir.absolutePath}",
    )

    if (shellCompatibilityMode) {
        env += "PROOT_NO_SECCOMP=1"
    }

    return TerminalSession(
        proot.absolutePath,
        filesDir.absolutePath,
        args.toTypedArray(),
        env.toTypedArray(),
        2_000,
        client,
    ).apply {
        mSessionName = root
    }
}

internal fun prepareWorkspaceTerminalSession(
    context: Context,
    workspaceManager: WorkspaceManager,
    root: String,
) {
    val appContext = context.applicationContext
    workspaceManager.ensureWorkspace(root)
    val linuxDir = workspaceManager.linuxDir(root)
    File(appContext.filesDir, FileFolders.SKILLS).mkdirs()
    RootfsPatcher().patch(
        linuxDir,
        RootfsPatchOptions(nameservers = appContext.activeDnsServers())
    )
}

internal fun workspaceRootfsReady(workspaceManager: WorkspaceManager, root: String): Boolean {
    val linuxDir = workspaceManager.linuxDir(root)
    return linuxDir.isDirectory && File(linuxDir, "bin/sh").isFile
}
private const val WORKSPACE_DIR = "/workspace"
private const val SKILLS_DIR = "/skills"
private const val BUILTIN_SKILLS_DIR = "/builtin_skills"

// 一个 URL 最多还原跨越的软换行行数(向上/向下各算), 足够覆盖任意真实 URL
internal const val URL_MAX_WRAP_ROWS = 50
