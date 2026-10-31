package heizige.kk.khatkit.app.core.ui.components.ai

import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import androidx.compose.material3.Text
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.repository.WorkspaceRepository
import heizige.kk.khatkit.workspace.WorkspaceFileEntry
import heizige.kk.khatkit.workspace.WorkspaceStorageArea
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.ui.icons.folder
import heizige.kk.khatkit.app.core.ui.icons.undo
import heizige.kk.khatkit.uikit.mochi.MochiHorizontalDivider

@Composable
fun WorkspaceCwdPickerSheet(
    sheetState: MochiSheetState,
    workspaceId: String,
    currentCwd: String?,
    onSelectCwd: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val workspaceRepository: WorkspaceRepository = rememberAppEntryPoint().workspaceRepository()

    var browsePath by remember { mutableStateOf(fromAbsolutePath(currentCwd)) }
    var entries by remember { mutableStateOf<List<WorkspaceFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(browsePath) {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                workspaceRepository.listFiles(workspaceId, WorkspaceStorageArea.FILES, browsePath)
            }
            entries = result.sortedWith(compareByDescending<WorkspaceFileEntry> { it.isDirectory }.thenBy { it.name })
            loading = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            entries = emptyList()
            loading = false
        }
    }

    // U7b-6：照 Mochi 示例形态直接写 MochiSheet（state 由调用方持有），不再走 visible 兼容层。
    val confirmSelection = {
        onSelectCwd(toAbsolutePath(browsePath))
        onDismiss()
    }
    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(
            title = stringResource(R.string.workspace_cwd_select_directory),
            actions = { MochiBarButton(stringResource(R.string.workspace_cwd_set), onClick = confirmSelection) },
        )
        val cards = listCardStyle()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.workspace_cwd_select_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MochiIconButton(
                    enabled = browsePath.isNotBlank(),
                    onClick = {
                        browsePath = browsePath.substringBeforeLast('/', missingDelimiterValue = "")
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(undo, contentDescription = null)
                }
                Text(
                    text = toAbsolutePath(browsePath),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            MochiHorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(cards.gap),
            ) {
                val dirs = entries.filter { it.isDirectory }
                itemsIndexed(dirs, key = { _, entry -> entry.path }) { index, entry ->
                    MochiOptionItem(
                        onClick = { browsePath = entry.path },
                        shape = cards.indexedShape(index, dirs.size),
                        titleContent = {
                            Text(
                                text = entry.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = folder,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }

                if (!loading && dirs.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.workspace_cwd_no_subdirectories),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            MochiHorizontalDivider()

            if (currentCwd != null) {
                MochiTextButton(onClick = {
                    onSelectCwd(null)
                    // U7b-6：MochiSheetState 没有 dismiss，关闭走调用方的 onDismiss（内部再 hide）。
                    onDismiss()
                },
                     shapes = ButtonDefaults.shapes(),) {
                    Text(stringResource(R.string.workspace_cwd_reset))
                }
            }
        }
    }
}

private const val WORKSPACE_PREFIX = "/workspace"

private fun toAbsolutePath(relativePath: String): String {
    return if (relativePath.isBlank()) WORKSPACE_PREFIX else "$WORKSPACE_PREFIX/$relativePath"
}

private fun fromAbsolutePath(absolutePath: String?): String {
    if (absolutePath.isNullOrBlank()) return ""
    return absolutePath.removePrefix("$WORKSPACE_PREFIX/").removePrefix(WORKSPACE_PREFIX)
}
