package heizige.kk.khatkit.workspace

import android.content.Context

/** Null means the process supplied its own output; do not translate command output. */
fun WorkspaceCommandResult.localizedFailure(context: Context): String? = failure?.let {
    context.getString(when (it) {
        WorkspaceCommandFailure.ROOTFS_MISSING -> R.string.workspace_error_rootfs_missing
        WorkspaceCommandFailure.PROOT_MISSING -> R.string.workspace_error_proot_missing
        WorkspaceCommandFailure.LOADER_MISSING -> R.string.workspace_error_loader_missing
    })
}
